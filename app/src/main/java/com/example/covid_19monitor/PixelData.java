package com.example.covid_19monitor;

import java.util.Date;

class PixelData<T> {
    final Date timestamp;
    final T measurement;

    PixelData(Date timestamp, T measurement) {
        this.timestamp = timestamp;
        this.measurement = measurement;
    }
}
