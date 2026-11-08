import ru.ilya.alitahome.PhoneInputServer;
import java.util.concurrent.*;
public class PhoneInputHarness {
 public static void main(String[] args)throws Exception {
  CountDownLatch done=new CountDownLatch(1);
  try(PhoneInputServer server=new PhoneInputServer("127.0.0.1",new byte[]{1},value->{if(value.equals("fixture-input"))System.out.println("ACCEPTED");else System.out.println("WRONG");done.countDown();})){
   System.out.println(server.url());System.out.flush();if(!done.await(20,TimeUnit.SECONDS))throw new Exception("Timeout");
  }
 }
}