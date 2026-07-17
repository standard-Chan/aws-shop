import {
  createProductDetailOptions,
  productDetailSummary,
  requestProductDetail,
  uniformProductId,
} from './common.js';

const SCENARIO_NAME = 'uniform';
const DISTRIBUTION_NAME = 'uniform-random-all-products';
const SUMMARY_FILE = 'k6/results/product-detail-cache-uniform-summary.json';

export const options = createProductDetailOptions('product_detail_cache_uniform');

export default function () {
  requestProductDetail(uniformProductId(), SCENARIO_NAME, DISTRIBUTION_NAME);
}

export function handleSummary(data) {
  return productDetailSummary(data, SCENARIO_NAME, DISTRIBUTION_NAME, SUMMARY_FILE);
}
