package org.springframework.test;
import org.springframework.test.Pig;


/**
 * 
 * @author bill.b.li
 */
public class MengPig implements Pig{
	
	@Override
    public void eat() {
        System.out.println("猪在吃");
    }

}
