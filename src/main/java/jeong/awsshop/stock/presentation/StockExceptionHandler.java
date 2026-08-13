package jeong.awsshop.stock.presentation;

import jeong.awsshop.stock.exception.InsufficientStockException;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import jeong.awsshop.stock.exception.StockNotFoundException;
import jeong.awsshop.stock.exception.StockProductNotFoundException;
import jeong.awsshop.stock.exception.StockQuantityOverflowException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StockController.class)
public class StockExceptionHandler {

    @ExceptionHandler(InvalidStockQuantityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidStockQuantity(InvalidStockQuantityException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler({
        StockNotFoundException.class,
        StockProductNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleStockNotFound(RuntimeException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler({
        InsufficientStockException.class,
        StockQuantityOverflowException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleStockConflict(RuntimeException exception) {
        return exception.getMessage();
    }
}
