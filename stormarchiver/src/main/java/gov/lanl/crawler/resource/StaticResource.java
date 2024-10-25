package gov.lanl.crawler.resource;

import java.io.File;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

@Path("/capture/nouse")

public class StaticResource {
	@GET
    @Path("/{docPath:.*}.{ext}")
    public Response getHtml(@PathParam("docPath") String docPath, @PathParam("ext") String ext, @HeaderParam("accept") String accept)
    {
        File file = new File(cleanDocPath(docPath) + "." + ext);
        return Response.ok(file).build();
    }

	
	@GET
    @Path("{docPath:.*}")
    public Response getFolder(@PathParam("docPath") String docPath)
    {
        File file = null;
        if ("".equals(docPath) || "/".equals(docPath))
        {
            file = new File("index.html");
        }
        else
        {
            file = new File(cleanDocPath(docPath) + "/index.html"); 
        }
        return Response.ok(file).build();
    }

    private String cleanDocPath(String docPath)
    {
        if (docPath.startsWith("/"))
        {
            return docPath.substring(1);
        }
        else
        {
            return docPath;
        }
    }


	
}
