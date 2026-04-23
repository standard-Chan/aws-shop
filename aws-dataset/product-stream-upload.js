const https = require('https');
const http = require('http');
const zlib = require('zlib');

function streamUpload(downloadUrl, uploadUrl) {
  https.get(downloadUrl, (res) => {
    const gunzip = zlib.createGunzip();

    const uploadReq = http.request(uploadUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/octet-stream',
        'Transfer-Encoding': 'chunked', // 🔥 스트리밍 업로드 핵심
      },
    }, (uploadRes) => {
      console.log('Upload status:', uploadRes.statusCode);
    });

    // 🔥 핵심 파이프라인
    res
    .pipe(gunzip)   // gz → jsonl 스트림
    .pipe(uploadReq); // 바로 서버로 전송

    res.on('error', console.error);
    uploadReq.on('error', console.error);
  });
}

// 실행
streamUpload(
    'https://mcauleylab.ucsd.edu/public_datasets/data/amazon_2023/raw/meta_categories/meta_Gift_Cards.jsonl.gz',
    'http://localhost:8080/api/data-import/upload?filename=meta_Gift_Cards'
);