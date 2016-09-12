/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.holodeckb2b.testhelpers;

import org.holodeckb2b.interfaces.config.IConfiguration;
import org.holodeckb2b.interfaces.core.IHolodeckB2BCore;
import org.holodeckb2b.interfaces.delivery.IDeliverySpecification;
import org.holodeckb2b.interfaces.delivery.IMessageDeliverer;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.events.IMessageProcessingEventProcessor;
import org.holodeckb2b.interfaces.pmode.IPModeSet;
import org.holodeckb2b.interfaces.submit.IMessageSubmitter;
import org.holodeckb2b.interfaces.workerpool.IWorkerPoolConfiguration;
import org.holodeckb2b.interfaces.workerpool.TaskConfigurationException;
import org.holodeckb2b.pmode.PModeManager;

/**
 * Is utility class for testing the e-SENS connector that simulates the Holodeck B2B Core.
 *
 * @author Sander Fieten <sander at holodeck-b2b.org>
 */
public class HolodeckCore implements IHolodeckB2BCore {

    private final Config  config;

    private IPModeSet pmodeSet;

    private IMessageProcessingEventProcessor eventProcessor;

    public HolodeckCore(final String homeDir) {
        this(homeDir, null, null);
    }

    public HolodeckCore(final String homeDir, final String pmodeValidatorClass) {
        this(homeDir, pmodeValidatorClass, null);
    }

    public HolodeckCore(final String homeDir, final String pmodeValidatorClass, final String pmodeStorageClass) {
        config = new Config(homeDir, pmodeValidatorClass, pmodeStorageClass);
    }

    @Override
    public IConfiguration getConfiguration() {
        return config;
    }

    @Override
    public IMessageDeliverer getMessageDeliverer(final IDeliverySpecification deliverySpec) throws MessageDeliveryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public IMessageSubmitter getMessageSubmitter() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public IPModeSet getPModeSet() {
        if (pmodeSet != null)
            pmodeSet = new PModeManager(config.getPModeValidatorImplClass(), config.getPModeStorageImplClass());

        return pmodeSet;
    }

    public void setEventProcessor(final IMessageProcessingEventProcessor processor) {
        eventProcessor = processor;
    }

    @Override
    public IMessageProcessingEventProcessor getEventProcessor() {
        return eventProcessor;
    }

    @Override
    public void setPullWorkerPoolConfiguration(final IWorkerPoolConfiguration pullConfiguration) throws TaskConfigurationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
