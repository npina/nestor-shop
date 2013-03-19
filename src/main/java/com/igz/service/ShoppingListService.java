package com.igz.service;

import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.gson.Gson;
import com.igz.entity.product.ProductDto;
import com.igz.entity.product.ProductManager;
import com.igz.entity.shoppinglist.ShoppingListDto;
import com.igz.entity.shoppinglist.ShoppingListManager;
import com.igz.entity.shoppinglistitem.ShoppingListItemDto;
import com.igz.entity.user.UserDto;
import com.igzcode.java.util.Trace;

/**
 * Shopping list servlet
 * 
 * GET /shoplist/all		getAllShoppingList
 * GET /shoplist/{id}		getShoppingList(String)
 * GET /shoplist/id/{id}	getAllShoppingList(Long)
 * POST /shoplist/item		putItemInShoppingList(Long)
 * 
 *
 */
@Path("/shoplist")
public class ShoppingListService {

	private static final Logger LOGGER = Logger.getLogger(ShoppingListManager.class.getName());
    private final ShoppingListManager slM = new ShoppingListManager();
    private final ProductManager productM = new ProductManager();
    
    /**
     * Get all the shopping lists of the user logged in the session
     * 
     * @return SC_OK
     */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    public Response getAllShoppingList( @Context HttpServletRequest p_request ) {
    	
    	UserDto user = (UserDto) (UserDto) p_request.getAttribute("USER");
    	LOGGER.info("/shoppinglist/all :" + user.getKey());
    	
    	List<ShoppingListDto> list = slM.findByUser(user);

    	if(list == null || list.isEmpty()) {
    		return Response.noContent().build();
    	} else {
    		return Response.ok().entity( new Gson().toJson( list ) ).build();
    	}
    }    
    
    /**
     * Get shopping list by webSafeString id
     * 
     * @return SC_OK
     */
    @GET
    @Path("/{id}")
    @Produces("application/json;charset=UTF-8")
    public Response getShoppingList( @PathParam("id") String listId, @Context HttpServletRequest p_request ) {
    	
    	ShoppingListDto sl = slM.getByKeyString(listId);

    	if(sl == null) {
    		throw new WebApplicationException(Status.NOT_FOUND);
    	} else {
    		List<ShoppingListItemDto> shoppingListItems = slM.getShoppingListItems(sl.getKey());
    		return Response.ok().entity( new Gson().toJson( shoppingListItems ) ).build();
    	}
    }
    
    /**
     * Get shopping list by id, where the user logged in the session is the owner
     * 
     * @return SC_OK
     */
    @GET
    @Path("/id/{id}")
    @Produces("application/json;charset=UTF-8")
    public Response getShoppingList( @PathParam("id") Long listId, @Context HttpServletRequest p_request ) {
    	
    	UserDto user = (UserDto) p_request.getAttribute("USER");
    	ShoppingListDto sl = slM.getByUserAndId(user, listId);

    	if(sl == null) {
    		throw new WebApplicationException(Status.NOT_FOUND);
    	} else {
    		List<ShoppingListItemDto> shoppingListItems = slM.getShoppingListItems(sl.getKey());
    		return Response.ok().entity( new Gson().toJson( shoppingListItems ) ).build();
    	}
    }    

    /**
     * Get shopping list by id, where the user logged in the session is the owner
     * 
     * @return SC_OK
     */
    @POST
    @Path("/item")
    @Produces("application/json;charset=UTF-8")
    public Response putItemInShoppingList( 
    		@FormParam("listId") Long listId,
  			@FormParam("productId") Long productId,
    		@Context HttpServletRequest p_request ) {
    	
    	UserDto user = (UserDto) p_request.getAttribute("USER");
    	ProductDto product = productM.getByLongId(productId);
    	ShoppingListDto shoppingList = slM.getByUserAndId(user, listId);
    	try {
    		ShoppingListItemDto item = slM.addProduct(shoppingList, product);
    		return Response.ok().entity( new Gson().toJson( item ) ).build();
    	} catch (IllegalArgumentException e) {
    		return errorResponse(e);
    	}
    }

	private Response errorResponse(String msg) {
		LOGGER.severe(msg);
		return Response.status( HttpServletResponse.SC_BAD_REQUEST).entity( msg ).build();
	}    
	
	private Response errorResponse(Exception e) {
		String error = Trace.error(e);
		LOGGER.severe(error);
		return Response.status( HttpServletResponse.SC_BAD_REQUEST).entity( error ).build();
	}  
}