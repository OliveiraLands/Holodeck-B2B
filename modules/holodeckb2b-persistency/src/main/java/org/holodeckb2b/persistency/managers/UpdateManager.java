/*
 * Copyright (C) 2017 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.persistency.managers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.RollbackException;

import org.holodeckb2b.common.messagemodel.MessageProcessingState;
import org.holodeckb2b.common.util.MessageUnitUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.messagemodel.Direction;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.persistency.IUpdateManager;
import org.holodeckb2b.interfaces.persistency.PersistenceException;
import org.holodeckb2b.interfaces.persistency.entities.IErrorMessageEntity;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.persistency.entities.MessageUnitEntity;
import org.holodeckb2b.persistency.jpa.ErrorMessage;
import org.holodeckb2b.persistency.jpa.MessageUnit;
import org.holodeckb2b.persistency.jpa.MessageUnitProcessingState;
import org.holodeckb2b.persistency.jpa.UserMessage;
import org.holodeckb2b.persistency.util.EntityManagerUtil;
import org.holodeckb2b.persistency.util.JPAEntityHelper;

/**
 * Is the default persistency provider's implementation of the {@link IUpdateManager} interface.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 * @since  3.0.0
 */
public class UpdateManager implements IUpdateManager {

    @Override
    public <T extends IMessageUnit, V extends IMessageUnitEntity> V storeMessageUnit(final T messageUnit)
                                                                                        throws PersistenceException {
        // If this is an outgoing message check its messageId is unique
    	if (messageUnit.getDirection() == Direction.OUT 
    		&& !Utils.isNullOrEmpty(HolodeckB2BCoreInterface.getQueryManager()
    											.getMessageUnitsWithId(messageUnit.getMessageId(), Direction.OUT)))
    		throw new PersistenceException("The messageId of the message unit (" + messageUnit.getMessageId() 
    										+ ") already exists!");
    	
    	EntityManager em = null;
        MessageUnit jpaMsgUnit = null;
        EntityTransaction tx = null;                
        try {
            // Determine which JPA class should be created to store the meta-data
            Class jpaEntityClass = JPAEntityHelper.determineJPAClass(messageUnit);
            Constructor cons = jpaEntityClass.getConstructor(MessageUnitUtils.getMessageUnitType(messageUnit));
            jpaMsgUnit = (MessageUnit) cons.newInstance(messageUnit);

            em = EntityManagerUtil.getEntityManager();
            tx = em.getTransaction();
            tx.begin();
            em.persist(jpaMsgUnit);
            tx.commit();
        } catch (NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        	
        		if (tx!=null && tx.isActive()) {
        			tx.rollback();
        		}
            // Could not create a JPA entity object for the given message unit
            throw new PersistenceException("An error occurred while saving the message unit's meta-data!", ex);
        } finally {
            if (em != null && em.isOpen())
                em.close();
        }

        return JPAEntityHelper.wrapInEntity(jpaMsgUnit, true);
    }

    @Override
    public boolean setProcessingState(final IMessageUnitEntity msgUnit, final ProcessingState currentProcState,
                                      final ProcessingState newProcState) throws PersistenceException {
    	return this.setProcessingState(msgUnit, currentProcState, newProcState, null);
    }
    
    @Override
    public boolean setProcessingState(final IMessageUnitEntity msgUnit, final ProcessingState currentProcState,
                                      final ProcessingState newProcState, final String description) 
                                    		  											  throws PersistenceException {
    	EntityManager em = null;
        EntityTransaction tx = null;
        try {
        	em = EntityManagerUtil.getEntityManager();
        	tx = em.getTransaction();
            tx.begin();
            // Reload the entity object from the database so we've actual data and a managed JPA object ready for change
            MessageUnit jpaMsgUnit = em.find(MessageUnit.class, ((MessageUnitEntity) msgUnit).getOID(),
                                             LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            // Check that the current state equals the required state
            MessageUnitProcessingState currentState = (MessageUnitProcessingState)
                                                                                jpaMsgUnit.getCurrentProcessingState();
            if (currentProcState != null && currentState.getState() != currentProcState) {
                // Not in the required state, stop execution
                em.getTransaction().rollback();
                return false;
            }
            // Current state is as requested, update to new state
            jpaMsgUnit.setProcessingState(new MessageProcessingState(newProcState, description));
            // Save to database, the flush is used to trigger possible an optimistic lock exception
            em.flush();
            tx.commit();
            
            // Ensure that the object stays completely loaded if it was already so previously
            if (msgUnit.isLoadedCompletely()) {
            	tx.begin();
                QueryManager.loadCompletely(jpaMsgUnit);
                tx.commit();
            }
            // Update the entity object
            ((MessageUnitEntity) msgUnit).updateJPAObject(jpaMsgUnit);
            return true;
        } catch (final OptimisticLockException | RollbackException alreadyChanged) {
            // During transaction the message unit was already updated, so state can not be changed.
            // Rollback and return false
	        	if (tx!=null && tx.isActive()) {
	    			tx.rollback();
	    		}
            return false;
        } catch (final Exception e) {
            // Another error occurred when updating the processing state. Rollback and rethrow as DatabaseException
            em.getTransaction().rollback();
            throw new PersistenceException("An error occurred while updating the processing state!", e);
        }finally {
            em.close();
        }
    }

    @Override
    public void deleteMessageUnit(final IMessageUnitEntity messageUnit) throws PersistenceException {
    		EntityManager em = null;
        EntityTransaction tx = null;
        try {
        		em = EntityManagerUtil.getEntityManager();
        		tx = em.getTransaction();
            tx.begin();
            // Reload the entity object from the database so we've actual data and a managed JPA object ready for change
            MessageUnit jpaMsgUnit = em.find(MessageUnit.class, ((MessageUnitEntity) messageUnit).getOID());
            em.remove(jpaMsgUnit);
            tx.commit();
        } catch (final Exception e) {
            // Something went wrong while executing the update, rollback the transaction (if active) and throw exception
	        	if (tx!=null && tx.isActive()) {
	    			tx.rollback();
	    		}
            throw new PersistenceException("An error occurred in the update of the message unit meta-data!", e);
        }finally {
            em.close();
        }
    }

    private void performUpdate(final MessageUnitEntity msgUnitEntity, final UpdateCallback update)
                                                                                          throws PersistenceException {
        EntityManager em = null;
        EntityTransaction tx = null;
        try {
        		em = EntityManagerUtil.getEntityManager();
        		tx = em.getTransaction();
            tx.begin();
            // Reload the entity object from the database so we've actual data and a managed JPA object ready for change
            MessageUnit jpaMsgUnit = em.find(MessageUnit.class, msgUnitEntity.getOID());
            update.perform(jpaMsgUnit);
            // Ensure that the object stays completely loaded if it was already so previously
            if (msgUnitEntity.isLoadedCompletely())
                QueryManager.loadCompletely(jpaMsgUnit);
            tx.commit();
            msgUnitEntity.updateJPAObject(jpaMsgUnit);
        } catch (final Exception e) {
            // Something went wrong while executing the update, rollback the transaction (if active) and throw exception
	        	if (tx!=null && tx.isActive()) {
	    			tx.rollback();
	    		}
            throw new PersistenceException("An error occurred in the update of the message unit meta-data!", e);
        }finally {
            em.close();
        }
    }

    @Override
    public void setPModeId(final IMessageUnitEntity msgUnit, final String pmodeId) throws PersistenceException {
        performUpdate((MessageUnitEntity) msgUnit, (MessageUnit jpaObject) -> {
            jpaObject.setPModeId(pmodeId);
        });
    }

    @Override
    public void setMultiHop(final IMessageUnitEntity msgUnit, final boolean isMultihop) throws PersistenceException {
        performUpdate((MessageUnitEntity) msgUnit, (MessageUnit jpaObject) -> {
            jpaObject.setMultiHop(isMultihop);
        });
    }

    @Override
    public void setPayloadInformation(final IUserMessageEntity userMessage, final Collection<IPayload> payloadInfo)
                                                                                        throws PersistenceException {
        performUpdate((MessageUnitEntity) userMessage, (MessageUnit jpaObject) -> {
            ((UserMessage) jpaObject).setPayloads(payloadInfo);
        });
    }

    @Override
    public void setAddSOAPFault(final IErrorMessageEntity errorMessage, final boolean addSOAPFault)
                                                                                        throws PersistenceException {
        performUpdate((MessageUnitEntity) errorMessage, (MessageUnit jpaObject) -> {
            ((ErrorMessage) jpaObject).setAddSOAPFault(addSOAPFault);
        });
    }

    interface UpdateCallback {
        void perform(final MessageUnit jpaObject);
    }
}
