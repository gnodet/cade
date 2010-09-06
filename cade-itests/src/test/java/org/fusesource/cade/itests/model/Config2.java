package org.fusesource.cade.itests.model;

import java.util.List;

import org.fusesource.cade.Meta;

@Meta.PID("org.fusesource.cade.itests.model.CustomPID")
public interface Config2 extends Config1 {

    @Meta.Separated
    List<String> tokens();

}
