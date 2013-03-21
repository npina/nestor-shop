package com.igz.entity.shoppinglist;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.igz.entity.product.ProductDto;
import com.igz.entity.shoppinglistitem.ShoppingListItemDto;
import com.igz.entity.shoppinglistitem.ShoppingListItemManager;
import com.igz.entity.user.UserDto;
import com.igz.exception.IgzException;

public class ShoppingListManager extends ShoppingListFactory {
	
	private static final Logger LOGGER = Logger.getLogger(ShoppingListManager.class.getName());

	/**
	 * Add a product to the shoppingList
	 * 
	 * If the product is already on the list, increment the quantity of the order by one
	 * 
	 * @param shoppingList
	 * @param product
	 * 
	 * @throws IllegalArgumentException - if shoppingList is not saved
	 * @return ShoppingListItemDto - The shoppingListItem created or incremented
	 * 
	 */
	public ShoppingListItemDto addProduct(ShoppingListDto shoppingList, ProductDto product) { 
		return addProduct(shoppingList, product, 1);
	}

	/**
	 * Add a product to the shoppingList specifying the quantity
	 * 
	 * If the product is already on the list, increment the quantity of the order by the 
	 * quantity specified
	 * 
	 * @param shoppingList
	 * @param product
	 * @param quantity
	 * 
	 * @throws IllegalArgumentException - if shoppingList is not saved
	 * @return ShoppingListItemDto - The shoppingListItem created or incremented
	 */
	public ShoppingListItemDto addProduct(final ShoppingListDto shoppingList, final ProductDto product, final int quantity ) {
		if(shoppingList == null || product == null) {
			throw new IllegalArgumentException("Shopping List and product must not be null");
		}
		if(shoppingList.getId() == null) {
			throw new IllegalArgumentException("Shopping List must have an id (saved state)");
		}
		LOGGER.info(String.format("%s: Adding product %s(%d) to %s list", 
					shoppingList.getOwner().getName(), product.getName(), quantity, shoppingList.getName()));
		
		return ofy().transact(new Work<ShoppingListItemDto>() {
			@Override
			public ShoppingListItemDto run() {
				final ShoppingListItemManager shoppingListItemM = new ShoppingListItemManager();
				ShoppingListItemDto item = shoppingListItemM.findProductInShoppingList(shoppingList.getKey(), product);
				if(item != null) {
					item = shoppingListItemM.updateOrder(item, quantity);
				} else {
					item = shoppingListItemM.createOrder(shoppingList.getKey(), product, quantity);
					shoppingList.setItemsDistinct(shoppingList.getItemsDistinct()+1);
					
				}
				shoppingList.setItemsTotal(shoppingList.getItemsTotal()+quantity);
				
				save (shoppingList);
				return item;
			}
		});
	}

	/**
	 * Removes a item from the shopping list updating the quantity counters on the parent list 
	 * 
	 * @param listKey
	 * @param itemId
	 */
	public void removeProduct(final Key<ShoppingListDto> listKey, final Long itemId) {
		ofy().transact(new VoidWork() {
			@Override
			public void vrun() {
				// Get objects from datastore
				ShoppingListDto list = getByKey(listKey);
				final ShoppingListItemManager shoppingListItemM = new ShoppingListItemManager();
				Key<ShoppingListItemDto> key = shoppingListItemM.getKey(listKey, itemId);
				ShoppingListItemDto item = shoppingListItemM.getByKey(key);

				// update counters 
				list.setItemsDistinct(list.getItemsDistinct()-1);
				list.setItemsTotal(list.getItemsTotal()-item.getQuantity());
				if(item.isBought()) {
					list.setItemsBought(list.getItemsBought()-item.getQuantity());
				}
				
				// persist changes
				shoppingListItemM.deleteByKey(key);
				save(list);
				
			}
		});
	}
	
	/**
	 * Sets the quantity of a product in the shopping list to the specified quantity.
	 * If the quantity is set to 0, this is the same as calling removeProduct 
	 * 
	 * @param listKey
	 * @param itemId
	 * @param quantity
	 * @throws IgzException - IGZ_SHOPPING_LIST_ITEM_NOT_FOUND if the item doesn't exists on the list
	 * @throws IllegalArgumentException - If any parameter is null or quantity < 0
	 * 
	 */
	public void setProductQuantity(final Key<ShoppingListDto> listKey, final Long itemId, final Integer quantity) throws IgzException {
		if(listKey == null || itemId == null || quantity == null || quantity.intValue() < 0) {
			throw new IllegalArgumentException("parameters must not be null and quantity must be > 0");
		}
		// TODO : Transaction, but with exceptions?
		// Get objects from datastore
		ShoppingListDto list = getByKey(listKey);
		final ShoppingListItemManager shoppingListItemM = new ShoppingListItemManager();
		Key<ShoppingListItemDto> key = shoppingListItemM.getKey(listKey, itemId);
		ShoppingListItemDto item = shoppingListItemM.getByKey(key);
		
		if(item==null) {
			throw new IgzException(IgzException.IGZ_INVALID_SHOPPING_LIST_ITEM);
		}
		
		if(quantity.intValue() == 0) {
			removeProduct(listKey, itemId);
		} else {
			// update counters
			int quantityChanged = quantity - item.getQuantity();
			list.setItemsTotal(list.getItemsTotal()+quantityChanged);
			if(item.isBought()) {
				list.setItemsBought(list.getItemsBought()+quantityChanged);
			}
			save(list);

			// update item quantity
			item.setQuantity(quantity);
			item.setDateModified(new Date());
			shoppingListItemM.save(item);
			
		}
		
	}

	/**
	 * Buy a product from a shopping list. If the product had quantity > 1, it buys all of it
	 * 
	 * @param listKey
	 * @param itemId
	 * @param boughtDate
	 * 
	 * @throws IgzException - IGZ_SHOPPING_LIST_ITEM_NOT_FOUND if the item doesn't exists on the list
	 * @throws IllegalArgumentException - If any parameter is null
	 */
	public void buyProduct(Key<ShoppingListDto> listKey, Long itemId, Date boughtDate) throws IgzException {
		if(listKey == null || itemId == null || boughtDate == null ) {
			throw new IllegalArgumentException("parameters must not be null ");
		}
		// Get objects from datastore
		ShoppingListDto list = getByKey(listKey);
		final ShoppingListItemManager shoppingListItemM = new ShoppingListItemManager();
		Key<ShoppingListItemDto> key = shoppingListItemM.getKey(listKey, itemId);
		ShoppingListItemDto item = shoppingListItemM.getByKey(key);
		
		if(item==null) {
			throw new IgzException(IgzException.IGZ_INVALID_SHOPPING_LIST_ITEM);
		}
		item.setDateBought(boughtDate);
		item.setBought(Boolean.TRUE);
		item.setDateModified(new Date());
		shoppingListItemM.save(item);	
		
		// update counters
		list.setItemsBought(list.getItemsBought()+item.getQuantity());
		save(list);
		
		
	}
	
	public List<ShoppingListDto> findByUser(UserDto user) {
		return findByParent(user.getKey());
	}
}
