package com.v2code.sfe4j.util;

import com.v2code.sfe4j.vo.BaseVO;

import java.io.Serializable;

public class NameValuePair extends BaseVO implements Serializable {
    private static final long serialVersionUID = 8364463926522622307L;
    private String name;
    private String value;

    public NameValuePair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}