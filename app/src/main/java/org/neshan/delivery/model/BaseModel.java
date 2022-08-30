package org.neshan.delivery.model;

public class BaseModel {
    private String id;
    private String name;

    public String getId() {
        return id;
    }

    public BaseModel setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public BaseModel setName(String name) {
        this.name = name;
        return this;
    }
}
