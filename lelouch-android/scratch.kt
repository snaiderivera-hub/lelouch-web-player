import androidx.media3.common.PlaybackException;
fun main() {
  val fields = PlaybackException::class.java.declaredFields;
  for (field in fields) {
    if (field.name.startsWith(\
ERROR_CODE_\)) {
      println(field.name + \
=\ + field.get(null));
    }
  }
}
