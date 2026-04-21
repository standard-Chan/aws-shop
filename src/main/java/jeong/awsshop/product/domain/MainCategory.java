package jeong.awsshop.product.domain;

public enum MainCategory {

    ALL_BEAUTY,
    AMAZON_FASHION,
    APPLIANCES,
    ARTS_CRAFTS_AND_SEWING,
    AUTOMOTIVE,
    BABY_PRODUCTS,
    BEAUTY_AND_PERSONAL_CARE,
    BOOKS,
    CDS_AND_VINYL,
    CELL_PHONES_AND_ACCESSORIES,
    CLOTHING_SHOES_AND_JEWELRY,
    DIGITAL_MUSIC,
    ELECTRONICS,
    GIFT_CARDS,
    GROCERY_AND_GOURMET_FOOD,
    HANDMADE,
    HEALTH_AND_HOUSEHOLD,
    HEALTH_AND_PERSONAL_CARE,
    HOME_AND_KITCHEN,
    INDUSTRIAL_AND_SCIENTIFIC,
    KINDLE_STORE,
    MAGAZINE_SUBSCRIPTIONS,
    MOVIES_AND_TV,
    MUSICAL_INSTRUMENTS,
    OFFICE_PRODUCTS,
    PATIO_LAWN_AND_GARDEN,
    PET_SUPPLIES,
    SOFTWARE,
    SPORTS_AND_OUTDOORS,
    SUBSCRIPTION_BOXES,
    TOOLS_AND_HOME_IMPROVEMENT,
    TOYS_AND_GAMES,
    VIDEO_GAMES,
    UNKNOWN;

    public static MainCategory fromDisplayName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }

        String normalized = value.trim().replaceAll("\\s+", " ").toUpperCase().replace(' ', '_');

        for (MainCategory category : values()) {
            if (category != UNKNOWN && category.name().equals(normalized)) {
                return category;
            }
        }
        return UNKNOWN;
    }

    /**
     * query parameter의 category 문자열을 enum으로 변환한다.
     */
    public static MainCategory fromQueryParam(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }
        return fromDisplayName(value.replace('-', ' '));
    }
}
