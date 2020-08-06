/*******************************************************************************
 * Copyright (c) 2020 Eurotech and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/

package org.eclipse.kura.example.wire.logic.multiport.provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.example.wire.logic.multiport.provider.LogicalComponentOptions.OperatorOption;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.graph.MultiportWireSupport;
import org.eclipse.kura.wire.multiport.MultiportWireReceiver;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogicalComponent implements WireEmitter, ConfigurableComponent, MultiportWireReceiver {

    private static final Logger logger = LoggerFactory.getLogger(LogicalComponent.class);

    private WireHelperService wireHelperService;
    private MultiportWireSupport wireSupport;

    protected LogicalComponentOptions options;
    protected BundleContext context;

    public void bindWireHelperService(final WireHelperService wireHelperService) {
        this.wireHelperService = wireHelperService;
    }

    @SuppressWarnings("unchecked")
    public void activate(final Map<String, Object> properties, ComponentContext componentContext) {
        logger.info("activating...");
        this.wireSupport = (MultiportWireSupport) this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        logger.info("activated, properties: {}", properties);
        this.context = componentContext.getBundleContext();
        updated(properties, componentContext);
        logger.info("activating...done");
    }

    public void updated(final Map<String, Object> properties, ComponentContext componentContext) {
        logger.info("updating...");
        this.options = new LogicalComponentOptions(properties, this.context);
        logger.info("updated, properties: {}", properties);
        this.options.getPortAggregatorFactory().build(this.wireSupport.getReceiverPorts())
                .onWireReceive(this::onWireReceive);

        logger.info("updating...done");
    }

    public synchronized void deactivate() {
        logger.info("deactivating...");
        logger.info("deactivating...done");
    }

    @Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }

    @Override
    public void updated(Wire wire, Object value) {
        this.wireSupport.updated(wire, value);
    }

    @Override
    public void producersConnected(Wire[] wires) {
        this.wireSupport.producersConnected(wires);
    }

    private Optional<Boolean> extractOperand(WireEnvelope wireEnvelope, String operandName) {
        if (wireEnvelope == null) {
            return Optional.empty();
        }
        final List<WireRecord> records = wireEnvelope.getRecords();
        if (records.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, TypedValue<?>> properties = records.get(0).getProperties();
        if (DataType.BOOLEAN.equals(properties.get(operandName).getType())) {
            return Optional.of((Boolean) properties.get(operandName).getValue());
        }
        return Optional.empty();

    }

    public void onWireReceive(List<WireEnvelope> wireEnvelopes) {
        final Optional<Boolean> firstOperand = extractOperand(wireEnvelopes.get(0), this.options.getFirstOperandName());
        final Optional<Boolean> secondOperand = extractOperand(wireEnvelopes.get(1),
                this.options.getSecondOperandName());
        final Optional<Boolean> result = performBooleanOperation(firstOperand, secondOperand);
        if (result.isPresent()) {
            WireRecord toBeEmitted = new WireRecord(
                    Collections.singletonMap(this.options.getResultName(), TypedValues.newBooleanValue(result.get())));
            this.wireSupport.emit(Collections.singletonList(toBeEmitted));
        }
    }

    private Optional<Boolean> performBooleanOperation(Optional<Boolean> firstOperand, Optional<Boolean> secondOperand) {
        if (firstOperand.isPresent()) {
            if (OperatorOption.NOT.equals(this.options.getBooleanOperation())) {
                return Optional.of(!firstOperand.get().booleanValue());
            } else if (secondOperand.isPresent()) {
                switch (this.options.getBooleanOperation()) {
                case AND:
                    return Optional.of(firstOperand.get().booleanValue() && secondOperand.get().booleanValue());
                case OR:
                    return Optional.of(firstOperand.get().booleanValue() || secondOperand.get().booleanValue());
                case NOR:
                    return Optional.of(!(firstOperand.get().booleanValue() || secondOperand.get().booleanValue()));
                case NAND:
                    return Optional.of(!(firstOperand.get().booleanValue() && secondOperand.get().booleanValue()));
                case XOR:
                    return Optional.of(firstOperand.get().booleanValue() ^ secondOperand.get().booleanValue());
                default:
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

}
