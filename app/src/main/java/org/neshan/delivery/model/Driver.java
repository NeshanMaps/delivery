package org.neshan.delivery.model;

public class Driver extends BaseModel {
    private double lat;
    private double lng;

    public double getLat() {
        return lat;
    }

    public Driver setLat(double lat) {
        this.lat = lat;
        return this;
    }

    public double getLng() {
        return lng;
    }

    public Driver setLng(double lng) {
        this.lng = lng;
        return this;
    }
}
