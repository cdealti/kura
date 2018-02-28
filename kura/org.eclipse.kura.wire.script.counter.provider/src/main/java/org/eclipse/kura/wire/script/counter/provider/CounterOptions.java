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

    public CounterOptions(Map<String, Object> properties) {
        this.name = String.valueOf(properties.get(COUNTER_NAME_PROP_NAME));
        this.startCondition = String.valueOf(START_CONDITION_PROP_NAME);
        this.stopCondition = String.valueOf(STOP_CONDITION_PROP_NAME);
        this.action = String.valueOf(ACTION_PROP_NAME);
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
