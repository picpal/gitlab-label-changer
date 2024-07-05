# Gitlab Project Merge Request Label Update

1. main method를 실행하여 테스트로 적용 여부 확인
   - GitlabLabelChanger.java 파일의 14 line GITLAB_PRIVATE_TOKEN 변수 변경
   - gitlab 계정의 access token 발급해서 넣어주세요
   - main method 실행
   - 동작 여부 확인
2. jar 파일로 빌드 하여 스케줄 실행할 때 사용 가능
   - gradle에서 shadowJar 실행 
     ```bash
        ./gradlew clean shadowJar
     ```
   - 또는 IDE에서 shadowJar 실행