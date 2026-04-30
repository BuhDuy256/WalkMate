package com.walkmate.domain.gamification;

public class BadgeCatalogItem {

    private final String name;
    private final String displayName;
    private final String description;
    private final String rarity;
    private final String category;

    public BadgeCatalogItem(String name, String displayName, String description,
                            String rarity, String category) {
        this.name        = name;
        this.displayName = displayName;
        this.description = description;
        this.rarity      = rarity;
        this.category    = category;
    }

    public String getName()        { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getRarity()      { return rarity; }
    public String getCategory()    { return category; }
}
