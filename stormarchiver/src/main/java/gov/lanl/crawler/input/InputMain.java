package gov.lanl.crawler.input;

import gov.lanl.crawler.core.InputArgs;

public class InputMain {
	 public static void main(String[] argsArray) throws Exception {
	        InputArgs args = new InputArgs( argsArray );
	       
	        
	        int port = args.getNumber( "port", InputServer.DEFAULT_PORT ).intValue();
	        String sdir = args.get("sdir", "/var/www");
	        System.out.println("sdir" + sdir);
	        final String baseUri = InputServer.getLocalhostBaseUri( port );
	        System.out.println(String.format("Running server at [%s]", baseUri));
	      //  ArchiveConfig.loadConfigFile();
	         InputServer.INSTANCE.startServer( port,sdir );
	        System.out.println("Press Ctrl-C to kill the server");
	        
	       
	       
	        Runtime.getRuntime().addShutdownHook( new Thread()
	        {
	            @Override public void run()
	            {
	                try
	                {
	                  //  System.out.println( "Shutting down the server" );
	                   InputServer.INSTANCE.stopServer();
	                 
	                }
	                catch ( Exception e )
	                {
	                    throw new RuntimeException( e );
	                }
	            }
	        } );
	    
	 try {
		 System.out.println(String.format("Jersey app started with WADL available at "
                 + "%sapplication.wadl\nTry out %stest\nHit enter to stop it...",
                 baseUri, baseUri));
	         System.in.read();
	 }
	 finally {
         InputServer.INSTANCE.stopServer();
     }
} 
}
