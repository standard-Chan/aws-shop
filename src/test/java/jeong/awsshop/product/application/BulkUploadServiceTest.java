package jeong.awsshop.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BulkUploadServiceTest {

    @Autowired
    private BulkUploadService bulkUploadService;

    @Autowired
    private ProductReadService productReadService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductFeatureRepository productFeatureRepository;

    @Autowired
    private ProductDescriptionRepository productDescriptionRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductVideoRepository productVideoRepository;

    @Autowired
    private ProductBoughtTogetherRepository productBoughtTogetherRepository;

    private static final String VALID_JSON_LINE = """
        {"main_category":"Handmade","title":"Daisy Keychain Wristlet Gray Fabric Key fob Lanyard","average_rating":4.5,"rating_number":12,"features":["High Quality Fabrics","Antique Brass Metallic Hardware","1\\" wide; Approx. 5-1/2\\" loop opening","Handmade in California"],"description":["This charming Daisy Fabric Keychain wristlet features an opening that loops around your wrist allowing your hands to be free to carry other things! This sweet floral daisy key fob will be your little dose of joy, lifting your spirits each time you reach for your keys! PRODUCT DETAILS: Approx. 7\\" long including the split ring to hold keys. Machine stitched over quality cotton fabric and firm interfacing on inside for structure yet comfortable to hold."],"price":null,"images":[{"thumb":"https://m.media-amazon.com/images/I/41J3kMGt34L._SS40_.jpg","large":"https://m.media-amazon.com/images/I/41J3kMGt34L.jpg","variant":"MAIN","hi_res":null},{"thumb":"https://m.media-amazon.com/images/I/41slBR2YGOL._SS40_.jpg","large":"https://m.media-amazon.com/images/I/41slBR2YGOL.jpg","variant":"PT01","hi_res":null},{"thumb":"https://m.media-amazon.com/images/I/41++pwWvfcL._SS40_.jpg","large":"https://m.media-amazon.com/images/I/41++pwWvfcL.jpg","variant":"PT02","hi_res":"https://m.media-amazon.com/images/I/51TpGYCdKIL._SL1000_.jpg"},{"thumb":"https://m.media-amazon.com/images/I/41JKZoroL3L._SS40_.jpg","large":"https://m.media-amazon.com/images/I/41JKZoroL3L.jpg","variant":"PT03","hi_res":"https://m.media-amazon.com/images/I/515dUKmwsbL._SL1000_.jpg"},{"thumb":"https://m.media-amazon.com/images/I/41xHTeVPFOL._SS40_.jpg","large":"https://m.media-amazon.com/images/I/41xHTeVPFOL.jpg","variant":"PT04","hi_res":"https://m.media-amazon.com/images/I/513oIHPmgUL._SL1000_.jpg"}],"videos":[],"store":"Generic","categories":["Handmade Products","Clothing, Shoes & Accessories","Luggage & Travel Gear","Key & Identification Accessories","Keychains & Keyrings"],"details":{"Package Dimensions":"8 x 4 x 0.85 inches; 0.81 Ounces","Department":"womens","Date First Available":"September 11, 2018"},"parent_asin":"B07NTK7T5P","bought_together":null}
        """;

    private static final String JSON_WITH_NULL_COLLECTIONS = """
        {"main_category":"Handmade","title":"Null Safe Product","average_rating":null,"rating_number":null,"features":null,"description":null,"price":null,"images":null,"videos":null,"store":null,"categories":null,"details":null,"parent_asin":"NULLSAFE001","bought_together":null}
        """;

    private static final String JSON_WITH_NULL_PARENT_ASIN = """
        {"main_category":"Handmade","title":"Missing Parent Asin","average_rating":4.0,"rating_number":1,"features":[],"description":[],"price":null,"images":[],"videos":[],"store":"Generic","categories":[],"details":null,"parent_asin":null,"bought_together":null}
        """;

    private static final String JSON_WITH_NULL_TITLE = """
        {"main_category":"Handmade","title":null,"average_rating":4.0,"rating_number":1,"features":[],"description":[],"price":null,"images":[],"videos":[],"store":"Generic","categories":[],"details":null,"parent_asin":"NULLTITLE001","bought_together":null}
        """;

    private static final String INVALID_JSON_LINE = """
        {"main_category":"Handmade","title":"Broken Json"
        """;

    @Test
    @DisplayName("JSONL 한 줄이 유효하면 상품과 자식 엔티티가 저장되어야 한다")
    void should_save_one_product_and_children_when_json_line_is_valid() {
        // Given: 적재 서비스와 유효한 JSON line

        // When: 한 줄을 insert 한다
        // Then: 저장 과정에서 예외가 없어야 한다
        assertThatCode(() -> bulkUploadService.insert(VALID_JSON_LINE))
                .doesNotThrowAnyException();

        // Then: 루트와 child entity가 저장되어야 한다
        assertThat(productRepository.count()).isEqualTo(1L);
        assertThat(productFeatureRepository.count()).isEqualTo(4L);
        assertThat(productDescriptionRepository.count()).isEqualTo(1L);
        assertThat(productCategoryRepository.count()).isEqualTo(5L);
        assertThat(productImageRepository.count()).isEqualTo(5L);
        assertThat(productVideoRepository.count()).isEqualTo(0L);
        assertThat(productBoughtTogetherRepository.count()).isEqualTo(0L);
        assertThat(productRepository.existsByParentAsin("B07NTK7T5P")).isTrue();
    }

    @Test
    @DisplayName("저장한 상품은 parentAsin으로 조회되어야 한다")
    void should_find_saved_product_when_query_by_parentAsin() {
        // Given: 저장 서비스와 조회 서비스, 그리고 유효한 JSON line

        // When: 상품을 insert 하고 parentAsin으로 조회한다
        bulkUploadService.insert(VALID_JSON_LINE);

        // Then: 조회 결과가 존재해야 한다
        assertThat(productReadService.findByParentAsin("B07NTK7T5P")).isPresent();
    }

    @Test
    @DisplayName("null 배열 값이 있어도 에러 없이 저장되어야 한다")
    void should_save_without_error_when_arrays_are_null() {
        // Given: null 컬렉션을 포함한 JSON line

        // When: insert를 수행한다
        // Then: 예외가 발생하지 않아야 한다
        assertThatCode(() -> bulkUploadService.insert(JSON_WITH_NULL_COLLECTIONS))
                .doesNotThrowAnyException();

        // Then: child entity는 생성되지 않아야 한다
        assertThat(productRepository.count()).isEqualTo(1L);
        assertThat(productFeatureRepository.count()).isEqualTo(0L);
        assertThat(productDescriptionRepository.count()).isEqualTo(0L);
        assertThat(productCategoryRepository.count()).isEqualTo(0L);
        assertThat(productImageRepository.count()).isEqualTo(0L);
        assertThat(productVideoRepository.count()).isEqualTo(0L);
        assertThat(productBoughtTogetherRepository.count()).isEqualTo(0L);
        assertThat(productRepository.existsByParentAsin("NULLSAFE001")).isTrue();
    }

    @Test
    @DisplayName("parentAsin이 null이면 저장되지 않아야 한다")
    void should_not_save_product_when_parentAsin_is_null() {
        // Given: parent_asin이 null인 JSON line

        // When: insert를 수행한다
        // Then: 예외 없이 무시되거나 스킵되어야 한다
        assertThatCode(() -> bulkUploadService.insert(JSON_WITH_NULL_PARENT_ASIN))
                .doesNotThrowAnyException();

        // Then: 저장된 상품이 없어야 한다
        assertThat(productRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("title이 null이면 저장되지 않아야 한다")
    void should_not_save_product_when_title_is_null() {
        // Given: title이 null인 JSON line

        // When: insert를 수행한다
        // Then: 예외 없이 무시되거나 스킵되어야 한다
        assertThatCode(() -> bulkUploadService.insert(JSON_WITH_NULL_TITLE))
                .doesNotThrowAnyException();

        // Then: 저장된 상품이 없어야 한다
        assertThat(productRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("유효하지 않은 JSON이면 저장되지 않아야 한다")
    void should_not_save_product_when_json_is_invalid() {
        // Given: 깨진 JSON line

        // When: insert를 수행한다
        // Then: 파싱 실패가 발생해도 저장은 되지 않아야 한다
        assertThatCode(() -> bulkUploadService.insert(INVALID_JSON_LINE))
                .doesNotThrowAnyException();

        // Then: 저장된 상품이 없어야 한다
        assertThat(productRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("이미 존재하는 parentAsin이면 에러가 발생해야 한다")
    void should_throw_error_when_parentAsin_is_duplicated() {
        // Given: 먼저 저장된 상품과 동일한 parentAsin을 가진 JSON line
        bulkUploadService.insert(VALID_JSON_LINE);

        // When: 같은 JSON line을 다시 insert 한다
        // Then: 중복 유니크 값 에러가 발생해야 한다
        assertThatThrownBy(() -> bulkUploadService.insert(VALID_JSON_LINE))
                .isInstanceOf(RuntimeException.class);

        // Then: 첫 번째 저장만 유지되어야 한다
        assertThat(productRepository.count()).isEqualTo(1L);
    }

}
