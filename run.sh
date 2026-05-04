cd ../Mindustry
./gradlew server:dist desktop:dist
cd ../Mindustry-Wiki-Generator
if [ -n "${WIKIGEN_ARGS:-}" ]; then
  ./gradlew run --args="$WIKIGEN_ARGS"
else
  ./gradlew run
fi