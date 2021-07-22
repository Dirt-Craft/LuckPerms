package me.lucko.luckperms.forge;

public enum TriState {
    TRUE,
    FALSE,
    UNDEFINED;

    public boolean asBool() {
        return this == TRUE;
    }

    public static TriState of(boolean b){
        return b? TRUE : FALSE;
    }

    public static TriState of(Boolean b){
        return b == null? UNDEFINED : of(b.booleanValue());
    }
}
