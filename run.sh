cd ../Mindustry
./gradlew server:dist desktop:dist
cd ../Mindustry-Wiki-Generator
./gradlew run --args="${WIKIGEN_ARGS:---lang=zh_CN}"