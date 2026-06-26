package com.fmorea.syncthing.model;

import java.util.Map;

public class Event {

    public int id;
    public int globalID;
    public String type;
    public String time;
    public Map<String, Object> data;

}
