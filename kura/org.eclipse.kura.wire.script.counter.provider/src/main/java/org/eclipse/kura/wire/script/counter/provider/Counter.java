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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Counter {

    private static final Logger logger = LoggerFactory.getLogger(Counter.class);

    private final String name;

    private final ScriptEngine scriptEngine;
    private final Bindings bindings;
    private final CompiledScript startCondition;
    private final CompiledScript stopCondition;
    private final CompiledScript action;

    private CounterState state;
    private WireEnvelope lastEnvelope;
    private WireEnvelope startEnvelope;
    private WireEnvelope stopEnvelope;
    private long startCount;
    private long stopCount;

    public Counter(CounterOptions options, ScriptEngine scriptEngine) throws ScriptException {
        this.name = options.getName();
        this.state = CounterState.STOPPED;
        this.lastEnvelope = new WireEnvelope("", new ArrayList<>());
        this.startEnvelope = this.lastEnvelope;
        this.stopEnvelope = this.lastEnvelope;

        this.scriptEngine = scriptEngine;
        this.bindings = createBindings();
        this.startCondition = ((Compilable) this.scriptEngine).compile(options.getStartCondition());
        this.stopCondition = ((Compilable) this.scriptEngine).compile(options.getStopCondition());
        this.action = ((Compilable) this.scriptEngine).compile(options.getAction());
    }

    public String getName() {
        return this.name;
    }

    public ScriptEngine getScriptEngine() {
        return this.scriptEngine;
    }

    public Bindings getBindings() {
        return this.bindings;
    }

    public CompiledScript getStartCondition() {
        return this.startCondition;
    }

    public CompiledScript getStopCondition() {
        return this.stopCondition;
    }

    public CompiledScript getAction() {
        return this.action;
    }

    public CounterState getState() {
        return this.state;
    }

    public WireEnvelope getLastEnvelope() {
        return this.lastEnvelope;
    }

    public WireEnvelope getStartedEnvelope() {
        return this.startEnvelope;
    }

    public WireEnvelope getStoppedEnvelope() {
        return this.stopEnvelope;
    }

    public long getStartedCount() {
        return this.startCount;
    }

    public long getStoppedCount() {
        return this.stopCount;
    }

    public List<WireRecord> update(WireEnvelope input) {
        List<WireRecord> result = null;
        this.lastEnvelope = input;

        updateBindings();

        if (this.state == CounterState.STOPPED) {
            if (evalBoolean(this.startCondition, this.bindings)) {
                this.state = CounterState.STARTED;
                this.startCount++;
                this.startEnvelope = this.lastEnvelope;
            }
        } else if (this.state == CounterState.STARTED) {
            if (evalBoolean(this.stopCondition, this.bindings)) {
                this.state = CounterState.STOPPED;
                this.stopCount++;
                this.stopEnvelope = this.lastEnvelope;
                updateBindings();
                result = fireAction();
            }
        } else {
            logger.error("Unknown state {}", this.state);
        }
        return result;
    }

    public List<WireRecord> fireAction() {
        try {
            this.action.eval(this.bindings);
        } catch (ScriptException e) {
            logger.error("Script evaluation failed", e);
        }
        return ((OutputWireRecordListWrapper) this.bindings.get("output")).getRecords();
    }

    public void updateBindings() {
        final WireEnvelopeWrapper inputEnvelopeWrapper = new WireEnvelopeWrapper(
                new WireRecordListWrapper(this.lastEnvelope.getRecords()), this.lastEnvelope.getEmitterPid());
        final OutputWireRecordListWrapper outputEnvelopeWrapper = new OutputWireRecordListWrapper();

        final WireEnvelopeWrapper startEnvelopeWrapper = new WireEnvelopeWrapper(
                new WireRecordListWrapper(this.startEnvelope.getRecords()), this.startEnvelope.getEmitterPid());
        final WireEnvelopeWrapper stopEnvelopeWrapper = new WireEnvelopeWrapper(
                new WireRecordListWrapper(this.stopEnvelope.getRecords()), this.stopEnvelope.getEmitterPid());

        this.bindings.put("input", inputEnvelopeWrapper);
        this.bindings.put("output", outputEnvelopeWrapper);

        this.bindings.put("startInput", startEnvelopeWrapper);
        this.bindings.put("stopInput", stopEnvelopeWrapper);

        this.bindings.put("startCount", this.startCount);
        this.bindings.put("stopCount", this.stopCount);
    }

    private Bindings createBindings() {
        Bindings bindingz = this.scriptEngine.createBindings();

        bindingz.put("logger", logger);

        bindingz.put("newWireRecord", (Supplier<WireRecordWrapper>) WireRecordWrapper::new);

        bindingz.put("newBooleanValue", (Function<Boolean, TypedValue<?>>) TypedValues::newBooleanValue);
        bindingz.put("newByteArrayValue", (Function<byte[], TypedValue<?>>) TypedValues::newByteArrayValue);
        bindingz.put("newDoubleValue",
                (Function<Number, TypedValue<?>>) num -> TypedValues.newDoubleValue(num.doubleValue()));
        bindingz.put("newFloatValue",
                (Function<Number, TypedValue<?>>) num -> TypedValues.newFloatValue(num.floatValue()));
        bindingz.put("newIntegerValue",
                (Function<Number, TypedValue<?>>) num -> TypedValues.newIntegerValue(num.intValue()));
        bindingz.put("newLongValue",
                (Function<Number, TypedValue<?>>) num -> TypedValues.newLongValue(num.longValue()));
        bindingz.put("newStringValue",
                (Function<Object, TypedValue<?>>) obj -> TypedValues.newStringValue(obj.toString()));

        bindingz.put("newByteArray", (Function<Integer, byte[]>) size -> new byte[size]);

        for (DataType type : DataType.values()) {
            bindingz.put(type.name(), type);
        }

        bindingz.remove("exit");
        bindingz.remove("quit");

        return bindingz;
    }

    private static boolean evalBoolean(CompiledScript script, Bindings bindings) {
        boolean result = false;
        try {
            Object o = script.eval(bindings);
            if (o instanceof Boolean) {
                result = (Boolean) o;
            }
        } catch (ScriptException e) {
            logger.error("Script evaluation failed", e);
        }
        return result;
    }
}
