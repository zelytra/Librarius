package zelytra.librarius.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A rating out of five by half stars (#484): 0.5, 1, 1.5 … 5. Null passes — whether a rating
 * is required is the field's own {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = HalfStar.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HalfStar {

    String message() default "must be between 0.5 and 5, by half stars";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** The bounds and the step, shared with the database checks of V34. */
    final class Validator implements ConstraintValidator<HalfStar, Double> {
        @Override
        public boolean isValid(Double value, ConstraintValidatorContext context) {
            return value == null || isRating(value);
        }

        public static boolean isRating(double value) {
            return value >= 0.5 && value <= 5 && value * 2 == Math.floor(value * 2);
        }
    }
}
