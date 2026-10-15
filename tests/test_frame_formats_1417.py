from pathlib import Path
import tempfile,subprocess,os
root=Path(__file__).resolve().parents[1]
parts=[]
for file,object_name in [('PhoneDualCamera.kt','PhoneDualFrames'),('PhoneMp4Recorder.kt','PhoneYuv420')]:
 s=(root/'app/src/main/java/com/minhacamera'/file).read_text(); start=s.index('internal object '+object_name+' {'); opening=s.index('{',start); depth=0
 for i in range(opening,len(s)):
  if s[i]=='{':depth+=1
  elif s[i]=='}':
   depth-=1
   if depth==0:
    parts.append(s[start:i+1]);break
content='\n'.join(parts)+'''
fun main() {
    val a=byteArrayOf(1,2,3,4,5,6)
    val b=byteArrayOf(11,12,13,14,15,16)
    val stacked=PhoneDualFrames.stack(a,b,2,2)
    check(stacked.contentEquals(byteArrayOf(1,2,3,4,11,12,13,14,5,6,15,16)))
    val input=byteArrayOf(1,2,3,4,5,6,7,8,9,10,11,12) // 4x2, 8 Y + 4 VU
    check(PhoneYuv420.convert(input,4,2,false).contentEquals(byteArrayOf(1,2,3,4,5,6,7,8,10,9,12,11)))
    check(PhoneYuv420.convert(input,4,2,true).contentEquals(byteArrayOf(1,2,3,4,5,6,7,8,10,12,9,11)))
    try { PhoneDualFrames.stack(byteArrayOf(1),b,2,2); error("aceitou quadro inválido") }
    catch (_: IllegalArgumentException) { }
    println("OK: 4 verificações de empilhamento e cores YUV")
}
'''
with tempfile.TemporaryDirectory() as tmp:
 src=Path(tmp)/'FramesSmoke.kt';src.write_text(content)
 jar=Path(tmp)/'frames.jar'
 subprocess.run(['kotlinc',str(src),'-include-runtime','-d',str(jar)],check=True,timeout=40)
 subprocess.run(['java','-jar',str(jar)],check=True,timeout=10)
