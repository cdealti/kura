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
import java.util.Map;

public class CounterOptions {

    private static final String COUNTER_NAME_PROP_NAME = "name";
    private static final String START_CONDITION_PROP_NAME = "start.condition";
    private static final String STOP_CONDITION_PROP_NAME = "stop.condition";
    private static final String ACTION_PROP_NAME = "action";

    private final String name;
    private final String startCondition;
    private final String stopCondition;
    private final String action;

    private CounterOptions(Map<String, Object> properties, int index) {
        this.name = getStringElement(properties.get(COUNTER_NAME_PROP_NAME), index);
        this.startCondition = getStringElement(properties.get(START_CONDITION_PROP_NAME), index);
        this.stopCondition = getStringElement(properties.get(STOP_CONDITION_PROP_NAME), index);
        this.action = getStringElement(properties.get(ACTION_PROP_NAME), index);
    }

    public static List<CounterOptions> newCounterOptionsList(Map<String, Object> properties) {
        int count = getCountersCount(properties);

        List<CounterOptions> lco = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CounterOptions options = new CounterOptions(properties, i);
            lco.add(options);
        }

        return lco;
    }

    private static String getStringElement(Object o, int index) {
        String value = ((String[]) o)[index];
        if (value == null) {
            value = "";
        }
        return value;
    }

    private static int getCountersCount(Map<String, Object> properties) {
        return ((String[]) properties.get(COUNTER_NAME_PROP_NAME)).length;
    }

    public String getName() {
        return this.name;
    }

    public String getStartCondition() {
        return this.startCondition;
    }

    public String getStopCondition() {
        return this.stopCondition;
    }

    public String getAction() {
        return this.action;
    }
}
