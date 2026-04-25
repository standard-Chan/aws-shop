package jeong.awsshop.product.service.dataimport;

public final class MainCategoryNormalizer {

    private MainCategoryNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim()
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .toUpperCase()
                .replace(' ', '_');

        return normalized.isBlank() ? null : normalized;
    }
}
