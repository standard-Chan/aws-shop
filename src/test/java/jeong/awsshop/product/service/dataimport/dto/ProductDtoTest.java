package jeong.awsshop.product.service.dataimport.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JSON_LINE = """
            {"main_category":"Gift Cards","title":"Amazon.com Gift Card in Gift Tag (Various Designs)","average_rating":4.8,"rating_number":1006,"features":["Gift Card is affixed inside a gift tag","Gift amount may not be printed on Gift Cards"],"description":["Amazon.com Gift Cards are redeemable for millions of items across Amazon.com."],"price":null,"images":[{"thumb":"https://m.media-amazon.com/images/I/41ZA96xtATL._SX38_SY50_CR,0,0,38,50_.jpg","large":"https://m.media-amazon.com/images/I/41ZA96xtATL.jpg","variant":"MAIN","hi_res":"https://m.media-amazon.com/images/I/71cWJvVGYtL._SL1500_.jpg"}],"videos":[],"store":"Amazon","categories":["Gift Cards","Gift Card Recipients","For Him"],"details":{"Package Dimensions":"5 x 3 x 0.1 inches; 0.63 Ounces","Item model number":"Fixed"},"parent_asin":"B06ZXTKYHN","bought_together":null}
            """;

    @Test
    @DisplayName("상품 JSON 한 줄은 ProductDto record로 역직렬화되어야 한다")
    void should_deserialize_json_line_to_productDto() throws Exception {
        ProductDto productDto = objectMapper.readValue(JSON_LINE, ProductDto.class);

        assertThat(productDto.mainCategory()).isEqualTo("Gift Cards");
        assertThat(productDto.title()).isEqualTo("Amazon.com Gift Card in Gift Tag (Various Designs)");
        assertThat(productDto.averageRating()).isEqualByComparingTo(new BigDecimal("4.8"));
        assertThat(productDto.ratingNumber()).isEqualTo(1006);
        assertThat(productDto.featuresOrEmpty()).hasSize(2);
        assertThat(productDto.descriptionsOrEmpty()).containsExactly(
                "Amazon.com Gift Cards are redeemable for millions of items across Amazon.com.");
        assertThat(productDto.imagesOrEmpty()).hasSize(1);
        assertThat(productDto.imagesOrEmpty().getFirst().variant()).isEqualTo("MAIN");
        assertThat(productDto.imagesOrEmpty().getFirst().hiRes())
                .isEqualTo("https://m.media-amazon.com/images/I/71cWJvVGYtL._SL1500_.jpg");
        assertThat(productDto.categoriesOrEmpty()).containsExactly("Gift Cards", "Gift Card Recipients", "For Him");
        assertThat(productDto.detailsOrEmpty())
                .containsEntry("Package Dimensions", "5 x 3 x 0.1 inches; 0.63 Ounces")
                .containsEntry("Item model number", "Fixed");
        assertThat(productDto.parentAsin()).isEqualTo("B06ZXTKYHN");
        assertThat(productDto.boughtTogether()).isNull();
    }
}
