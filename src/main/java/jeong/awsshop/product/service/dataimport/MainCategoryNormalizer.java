package jeong.awsshop.product.service.dataimport;

public final class MainCategoryNormalizer {

    private MainCategoryNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "UNKNOWN";
        }

        String normalized = value.trim()
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .toUpperCase()
                .replace(' ', '_');

        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }
}
