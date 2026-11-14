package com.marketlab.model;

import java.time.LocalDateTime;

public record PricePoint(LocalDateTime time, double price) {}

