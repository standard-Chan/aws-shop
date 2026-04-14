# application.yml

3가지가 존재함.

```text
application.yml
application-dev.yml
application-prod.yml
```

### 기본 = dev 모드 실행 방법

기본적으로 application.yml 실행 시, active=dev 이므로, application-dev.yml이 로드되어 덮어씌워짐.

```bash
$ ./gradlew bootRun
# 혹은
$ ./gradlew bootRun -Dspring.profiles.active=dev
```

### 배포모드 실행 방법

그래서 배포모드로 실행 시에는, spring.profiles.active=prod 옵션을 추가하여, application-prod.yml이 로드되어 덮어씌워지도록 한다.

```bash
$ ./gradlew bootRun -Dspring.profiles.active=dev
```

### 참고
`-Dspring.profiles.active={name}` 옵션은 application-{name}.yml 파일을 로드하여, application.yml의 설정을 덮어씌우는 역할을 한다. 
따라서, dev 모드에서는 application-dev.yml이, prod 모드에서는 application-prod.yml이 각각 로드되어, 환경에 맞는 설정이 적용된다.