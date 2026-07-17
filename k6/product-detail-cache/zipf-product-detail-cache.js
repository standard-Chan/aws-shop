import {
  createProductDetailOptions,
  productDetailSummary,
  requestProductDetail,
  zipfProductId,
} from './common.js';

const SCENARIO_NAME = 'zipf';
const DISTRIBUTION_NAME = 'zipf-alpha-1.0';
const ZIPF_ALPHA = 1.0;
const SUMMARY_FILE = __ENV.SUMMARY_FILE || 'k6/results/product-detail-cache-zipf-summary.json';

export const options = createProductDetailOptions('product_detail_cache_zipf');

export default function () {
  requestProductDetail(zipfProductId(ZIPF_ALPHA), SCENARIO_NAME, DISTRIBUTION_NAME);
}

export function handleSummary(data) {
  return productDetailSummary(data, SCENARIO_NAME, DISTRIBUTION_NAME, SUMMARY_FILE);
}
