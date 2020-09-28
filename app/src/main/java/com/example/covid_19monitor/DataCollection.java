package com.example.covid_19monitor;

import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

class DataCollection {
    private final CopyOnWriteArrayList<PixelData<Integer>> measurements = new CopyOnWriteArrayList<>();
    private int minimum = Integer.MIN_VALUE;
    private int maximum = Integer.MAX_VALUE;
    private final int rollingAverageSize = 4;

    void add(int measurement) {
        PixelData<Integer> measurementWithDate = new PixelData<>(new Date(), measurement);

        measurements.add(measurementWithDate);
        if (measurement < minimum) minimum = measurement;
        if (measurement > maximum) maximum = measurement;
    }

    CopyOnWriteArrayList<PixelData<Integer>> getLastStdValues(int count) {
        if (count < measurements.size()) {
            return  new CopyOnWriteArrayList<>(measurements.subList(measurements.size() - 1 - count, measurements.size() - 1));
        } else {
            return measurements;
        }
    }

    Date getLastTimestamp() {
        return measurements.get(measurements.size() - 1).timestamp;
    }
}