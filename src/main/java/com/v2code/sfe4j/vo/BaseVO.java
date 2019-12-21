package com.v2code.sfe4j.vo;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.exception.ContextedRuntimeException;

import java.io.Serializable;

public abstract class BaseVO implements Serializable, Cloneable {

    private static final long serialVersionUID = 2618192279106780874L;

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(17, 37, this);
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        try {
            return BeanUtils.cloneBean(this);
        } catch (Exception e) {
            throw new ContextedRuntimeException("Error cloning value object", e)
                .addContextValue("class", this.getClass().getName());
        }
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }

}
