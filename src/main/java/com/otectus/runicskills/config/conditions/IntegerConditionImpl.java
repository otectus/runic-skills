package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.config.models.TitleModel;

public abstract class IntegerConditionImpl extends ConditionImpl<Integer> {

    public IntegerConditionImpl(String conditionName) {
        super(conditionName);
    }

    @Override
    public boolean MeetCondition(String value, TitleModel.EComparator comparator) {
        int parsedValue = Integer.parseInt(value);

        return switch (comparator) {
            case EQUALS -> getProcessedValue().equals(parsedValue);
            case GREATER -> getProcessedValue() > parsedValue;
            case LESS -> getProcessedValue() < parsedValue;
            case GREATER_OR_EQUAL -> getProcessedValue() >= parsedValue;
            case LESS_OR_EQUAL -> getProcessedValue() <= parsedValue;
            default -> false;
        };
    }
}
