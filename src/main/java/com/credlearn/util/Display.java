package com.credlearn.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("display")
public class Display {
  @Value("${credlearn.zone}")
  private String zone;

  public String time(Instant instant) {
    return DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a")
        .withZone(ZoneId.of(zone))
        .format(instant);
  }

  public String initials(String name) {
    String[] p = name.trim().split("\s+");
    return (p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : ""))
        .toUpperCase();
  }
}
