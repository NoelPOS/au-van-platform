package com.auvan.api.notification.service;

import java.util.List;
import java.util.Map;

record RichMenu(Size size, boolean selected, String name, String chatBarText, List<Area> areas) {
    // The bounds match the tile edges drawn in scripts/line/rich-menu.html.
    static RichMenu of(String name, String liffUrl, String siteUrl) {
        return new RichMenu(new Size(2500, 1686), true, name, "Menu", List.of(
                new Area(new Bounds(0, 0, 1875, 900), link("Book a seat", liffUrl)),
                new Area(new Bounds(1875, 0, 625, 900), link("My tickets", liffUrl + "/tickets")),
                new Area(new Bounds(0, 900, 625, 786), link("How to pay", liffUrl + "/tickets")),
                new Area(new Bounds(625, 900, 625, 786), link("Trip updates", liffUrl + "/tickets")),
                new Area(new Bounds(1250, 900, 625, 786),
                        Map.of("type", "message", "label", "Contact staff", "text", "I need help from staff")),
                new Area(new Bounds(1875, 900, 625, 786), link("About AU-Van", siteUrl))));
    }

    private static Map<String, String> link(String label, String uri) {
        return Map.of("type", "uri", "label", label, "uri", uri);
    }

    record Size(int width, int height) { }

    record Bounds(int x, int y, int width, int height) { }

    record Area(Bounds bounds, Map<String, String> action) { }
}
