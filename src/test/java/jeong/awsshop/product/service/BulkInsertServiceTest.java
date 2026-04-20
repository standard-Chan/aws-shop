package jeong.awsshop.product.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import jeong.awsshop.product.repository.ProductBoughtTogetherRepository;
import jeong.awsshop.product.repository.ProductCategoryRepository;
import jeong.awsshop.product.repository.ProductDescriptionRepository;
import jeong.awsshop.product.repository.ProductFeatureRepository;
import jeong.awsshop.product.repository.ProductImageRepository;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.ProductVideoRepository;
import jeong.awsshop.product.service.dataimport.BulkInsertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class BulkInsertServiceTest {

    @Autowired
    private BulkInsertService bulkInsertService;

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

    private static final String BULK_INSERT_JSONL = """
            {"main_category": "Gift Cards", "title": "Amazon.com Gift Card in Gift Tag (Various Designs)", "average_rating": 4.8, "rating_number": 1006, "features": ["Gift Card is affixed inside a gift tag", "Gift amount may not be printed on Gift Cards", "Gift Card has no fees and no expiration date", "No returns and no refunds on Gift Cards", "Gift Card is redeemable towards millions of items storewide at Amazon.com", "Scan and redeem any Gift Card with a mobile or tablet device via the Amazon App", "Free One-Day Shipping (where available)", "Customized gift message, if chosen at check-out, only appears on packing slip and not on the actual gift card or carrier"], "description": ["Amazon.com Gift Cards are the perfect way to give them exactly what they're hoping for - even if you don't know what it is. Amazon.com Gift Cards are redeemable for millions of items across Amazon.com. Item delivered is a single physical Amazon.com Gift Card nested inside or with a free gift accessory."], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/41ZA96xtATL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/41ZA96xtATL.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/71cWJvVGYtL._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41NK1FX6uUL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/41NK1FX6uUL.jpg", "variant": "PT01", "hi_res": "https://m.media-amazon.com/images/I/71q-qp4b3-L._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41Y45S0GirL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/41Y45S0GirL.jpg", "variant": "PT02", "hi_res": "https://m.media-amazon.com/images/I/71KutAnl9gL._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/417MZ16DhcL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/417MZ16DhcL.jpg", "variant": "PT03", "hi_res": "https://m.media-amazon.com/images/I/61FMUKaXfJL._SL1175_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/21-tRQuOBZL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/21-tRQuOBZL.jpg", "variant": "PT12", "hi_res": "https://m.media-amazon.com/images/I/61blLcj3pWL._SL1500_.jpg"}], "videos": [], "store": "Amazon", "categories": ["Gift Cards", "Gift Card Recipients", "For Him"], "details": {"Package Dimensions": "5 x 3 x 0.1 inches; 0.63 Ounces", "Item model number": "Fixed", "Date First Available": "August 29, 2017", "Manufacturer": "Amazon"}, "parent_asin": "B06ZXTKYHN", "bought_together": null}
            {"main_category": "SUBSCRIPTION BOXES", "title": "Loved Again Media - Movie Subscription Box - 10 DVD Box - Pick Your Genres", "average_rating": 4.1, "rating_number": 75, "features": ["10 gently used DVDs delivered to your door every month.", "All titles are currated to your selection of specific genres. With 14+ genres for you to build your box from.", "We have hundreds of thousands of movies to pick from so this box will remain unique to your taste for years to come.", "Choose from Action, Adventure, Horror, Kids, Drama, Comedy, Romance, Thrillers, Documentaries, and a ton more!", "Help the environment by keeping used media out of landfills and by giving it a second life. We do our best to minimize all waste and rehome the movies."], "description": [], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/61FfqGgIMNL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61FfqGgIMNL._AC_.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/91Qthwjgl+L._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61tLIBYnjhL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61tLIBYnjhL._AC_.jpg", "variant": "PT11", "hi_res": "https://m.media-amazon.com/images/I/915mdTERF4L._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61+67xLBGlL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61+67xLBGlL._AC_.jpg", "variant": "PT12", "hi_res": "https://m.media-amazon.com/images/I/91KqI4Q3CtL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/6186F+gYXTL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/6186F+gYXTL._AC_.jpg", "variant": "PT13", "hi_res": "https://m.media-amazon.com/images/I/91iMalZnUIL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61sDRGqoT2L._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61sDRGqoT2L._AC_.jpg", "variant": "PT14", "hi_res": "https://m.media-amazon.com/images/I/91Q3UAI6CdL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61Rn7cebWaL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61Rn7cebWaL._AC_.jpg", "variant": "PT15", "hi_res": "https://m.media-amazon.com/images/I/91YykP+uBnL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61b3iiv+EVL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61b3iiv+EVL._AC_.jpg", "variant": "PT16", "hi_res": "https://m.media-amazon.com/images/I/91dLynCDvWL._AC_SL1500_.jpg"}], "videos": [], "store": "Loved Again Media", "categories": [], "details": {}, "parent_asin": "B08W5BSH6V", "bought_together": null}
            {"main_category": "Handmade", "title": "Daisy Keychain Wristlet Gray Fabric Key fob Lanyard", "average_rating": 4.5, "rating_number": 12, "features": ["High Quality Fabrics", "Antique Brass Metallic Hardware", "1\\" wide; Approx. 5-1/2\\" loop opening", "Handmade in California"], "description": ["This charming Daisy Fabric Keychain wristlet features an opening that loops around your wrist allowing your hands to be free to carry other things! This sweet floral daisy key fob will be your little dose of joy, lifting your spirits each time you reach for your keys! PRODUCT DETAILS: Approx. 7\\" long including the split ring to hold keys. Machine stitched over quality cotton fabric and firm interfacing on inside for structure yet comfortable to hold."], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/41J3kMGt34L._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41J3kMGt34L.jpg", "variant": "MAIN", "hi_res": null}, {"thumb": "https://m.media-amazon.com/images/I/41slBR2YGOL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41slBR2YGOL.jpg", "variant": "PT01", "hi_res": null}, {"thumb": "https://m.media-amazon.com/images/I/41++pwWvfcL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41++pwWvfcL.jpg", "variant": "PT02", "hi_res": "https://m.media-amazon.com/images/I/51TpGYCdKIL._SL1000_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41JKZoroL3L._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41JKZoroL3L.jpg", "variant": "PT03", "hi_res": "https://m.media-amazon.com/images/I/515dUKmwsbL._SL1000_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41xHTeVPFOL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41xHTeVPFOL.jpg", "variant": "PT04", "hi_res": "https://m.media-amazon.com/images/I/513oIHPmgUL._SL1000_.jpg"}], "videos": [], "store": "Generic", "categories": ["Handmade Products", "Clothing, Shoes & Accessories", "Luggage & Travel Gear", "Key & Identification Accessories", "Keychains & Keyrings"], "details": {"Package Dimensions": "8 x 4 x 0.85 inches; 0.81 Ounces", "Department": "womens", "Date First Available": "September 11, 2018"}, "parent_asin": "B07NTK7T5P", "bought_together": null}
            {"main_category": "Handmade", "title": "Silver Triangle Earrings with Chevron Pattern", "average_rating": 5.0, "rating_number": 1, "features": [], "description": ["These large silver triangles are stamped with a unique chevron pattern, adding a statement to any outfit. Made of tarnish resistant argentium silver, each pair is one of a kind, and hang from handcrafted argentium silver earring wires. Triangle measures approximatly 1 1/2\\" long."], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/514fSLJnX9L._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/514fSLJnX9L.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/91wlFDKyz9L._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/51riti9SiXL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/51riti9SiXL.jpg", "variant": "PT01", "hi_res": "https://m.media-amazon.com/images/I/81YHcIyddKL._SL1500_.jpg"}], "videos": [], "store": "Zo\\u00eb Noelle Designs", "categories": ["Handmade Products", "Jewelry", "Earrings", "Drop & Dangle"], "details": {"Department": "Women", "Date First Available": "July 4, 2016"}, "parent_asin": "B01HYNE114", "bought_together": null}
            """;

    @Test
    @DisplayName("JSONL InputStream을 bulk insert 하면 상품과 자식 엔티티가 실제 저장되어야 한다")
    void should_save_products_and_children_when_bulk_insert_jsonl_stream_is_given() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(BULK_INSERT_JSONL.getBytes(UTF_8));

        assertThatCode(() -> bulkInsertService.bulkInsert(inputStream))
                .doesNotThrowAnyException();

        assertThat(productRepository.count()).isEqualTo(4L);
        assertThat(productFeatureRepository.count()).isEqualTo(17L);
        assertThat(productDescriptionRepository.count()).isEqualTo(3L);
        assertThat(productCategoryRepository.count()).isEqualTo(12L);
        assertThat(productImageRepository.count()).isEqualTo(19L);
        assertThat(productVideoRepository.count()).isEqualTo(0L);
        assertThat(productBoughtTogetherRepository.count()).isEqualTo(0L);
        assertThat(productRepository.existsByParentAsin("B06ZXTKYHN")).isTrue();
        assertThat(productRepository.existsByParentAsin("B08W5BSH6V")).isTrue();
        assertThat(productRepository.existsByParentAsin("B07NTK7T5P")).isTrue();
        assertThat(productRepository.existsByParentAsin("B01HYNE114")).isTrue();
    }
}
