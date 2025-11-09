package org.example;

import java.io.Serializable;

public class Pair implements Serializable {
    private String v;
    private String t;

    public Pair(String v, String t) {
        this.v = v;
        this.t = t;
    }

    public String getV() {
        return v;
    }

    public String getT() {
        return t;
    }

    @Override
    public String toString() {
        return "Pair[" + v + ", " + t + "]";
    }
}
