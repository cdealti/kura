/*******************************************************************************
 * Copyright (c) 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.wire.script.counter.provider;

import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.service.component.ComponentException;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

public class ScriptCounter implements WireEmitter, WireReceiver, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(ScriptCounter.class);

    private volatile WireHelperService wireHelperService;
    private WireSupport wireSupport;

    private ScriptEngine scriptEngine;
    private Counter counter;

    public void bindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == null) {
            this.wireHelperService = wireHelperService;
        }
    }

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    public void activate(final Map<String, Object> properties) throws ComponentException {
        logger.info("Activating...");
        this.wireSupport = this.wireHelperService.newWireSupport(this);

        this.scriptEngine = createEngine();

        updated(properties);

        logger.info("... Activating done");
    }

    public void deactivate() {
        logger.info("Deactivating...");
        logger.info("... Deactivating done");
    }

    public synchronized void updated(final Map<String, Object> properties) {
        logger.info("Updating...");

        CounterOptions options = new CounterOptions(properties);
        try {
            this.counter = new Counter(options, this.scriptEngine);
        } catch (ScriptException e) {
            logger.error("Failed to create counter", e);
        }

        logger.info("... Updating done");
    }

    @Override
    public synchronized void onWireReceive(WireEnvelope wireEnvelope) {
        if (this.counter == null) {
            logger.warn("Counter is null");
            return;
        }

        final List<WireRecord> result = this.counter.update(wireEnvelope);
        if (result != null) {
            this.wireSupport.emit(result);
        }
    }

    private ScriptEngine createEngine() {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        ScriptEngine scriptNgine = factory.getScriptEngine(className -> false);

        if (scriptNgine == null) {
            throw new IllegalStateException("Error getting ScriptEngine");
        }

        final Bindings engineScopeBindings = scriptNgine.getBindings(ScriptContext.ENGINE_SCOPE);
        if (engineScopeBindings != null) {
            engineScopeBindings.remove("exit");
            engineScopeBindings.remove("quit");
        }

        final Bindings globalScopeBindings = scriptNgine.getBindings(ScriptContext.GLOBAL_SCOPE);
        if (globalScopeBindings != null) {
            globalScopeBindings.remove("exit");
            globalScopeBindings.remove("quit");
        }

        return scriptNgine;
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
}
