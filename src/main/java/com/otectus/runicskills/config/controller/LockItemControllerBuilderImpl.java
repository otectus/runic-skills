package com.otectus.runicskills.config.controller;

import com.otectus.runicskills.config.models.LockItem;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.impl.controller.AbstractControllerBuilderImpl;

public class LockItemControllerBuilderImpl extends AbstractControllerBuilderImpl<LockItem> implements LockItemControllerBuilder {
    public LockItemControllerBuilderImpl(Option<LockItem> option) {
        super(option);
    }

    @Override
    public Controller<LockItem> build() {
        return new LockItemController(option);
    }
}
