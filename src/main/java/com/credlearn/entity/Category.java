package com.credlearn.entity;

public enum Category {
  PROGRAMMING("Programming"),
  WEB_DEVELOPMENT("Web Development"),
  DESIGN("Design"),
  LANGUAGES("Languages"),
  MATHEMATICS("Mathematics"),
  MUSIC("Music"),
  COMMUNICATION("Communication"),
  OTHER("Other");
  private final String label;

  Category(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
