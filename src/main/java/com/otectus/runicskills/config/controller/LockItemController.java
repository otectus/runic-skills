package com.otectus.runicskills.config.controller;

import com.otectus.runicskills.config.models.LockItem;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.gui.controllers.dropdown.AbstractDropdownController;

public class LockItemController extends AbstractDropdownController<LockItem> {

    public LockItemController(Option<LockItem> option){
        super(option);
    }

    @Override
    public String getString() {
        return option.pendingValue().toString();
    }

    @Override
    public void setFromString(String value) {
        // No-op: free-text entry on the lock-item dropdown is not supported.
        // The user selects entries from the dropdown list; typing arbitrary text
        // leaves the current value unchanged.
    }
}
