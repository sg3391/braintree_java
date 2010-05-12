package com.braintreegateway;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.braintreegateway.exceptions.ForgedQueryStringException;
import com.braintreegateway.exceptions.NotFoundException;

public class CreditCardTest {

    private BraintreeGateway gateway;

    @Before
    public void createGateway() {
        this.gateway = new BraintreeGateway(Environment.DEVELOPMENT, "integration_merchant_id", "integration_public_key",
                "integration_private_key");
    }

    @Test
    public void transparentRedirectURLForCreate() {
        Assert.assertEquals(gateway.baseMerchantURL() + "/payment_methods/all/create_via_transparent_redirect_request",
                gateway.creditCard().transparentRedirectURLForCreate());
    }

    @Test
    public void transparentRedirectURLForUpdate() {
        Assert.assertEquals(gateway.baseMerchantURL() + "/payment_methods/all/update_via_transparent_redirect_request",
                gateway.creditCard().transparentRedirectURLForUpdate());
    }

    @Test
    public void trData() {
        String trData = gateway.trData(new CreditCardRequest(), "http://example.com");
        TestHelper.assertValidTrData(gateway.getConfiguration(), trData);
    }

    @Test
    public void create() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();
        Assert.assertEquals("John Doe", card.getCardholderName());
        Assert.assertEquals("MasterCard", card.getCardType());
        Assert.assertEquals("510510", card.getBin());
        Assert.assertEquals("05", card.getExpirationMonth());
        Assert.assertEquals("2012", card.getExpirationYear());
        Assert.assertEquals("05/2012", card.getExpirationDate());
        Assert.assertEquals("5100", card.getLast4());
        Assert.assertEquals("510510******5100", card.getMaskedNumber());
        Assert.assertTrue(card.getToken() != null);
        Assert.assertEquals(Calendar.getInstance().get(Calendar.YEAR), card.getCreatedAt().get(Calendar.YEAR));
        Assert.assertEquals(Calendar.getInstance().get(Calendar.YEAR), card.getUpdatedAt().get(Calendar.YEAR));
    }

    @Test
    public void createWithXmlCharacters() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("Special Chars <>&\"'").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();
        Assert.assertEquals("Special Chars <>&\"'", card.getCardholderName());
    }

    @Test
    public void createWithAddress() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            billingAddress().
                streetAddress("1 E Main St").
                extendedAddress("Unit 2").
                locality("Chicago").
                region("Illinois").
                postalCode("60607").
                countryName("United States of America").
                done().
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();

        Address billingAddress = card.getBillingAddress();
        Assert.assertEquals("1 E Main St", billingAddress.getStreetAddress());
        Assert.assertEquals("Unit 2", billingAddress.getExtendedAddress());
        Assert.assertEquals("Chicago", billingAddress.getLocality());
        Assert.assertEquals("Illinois", billingAddress.getRegion());
        Assert.assertEquals("60607", billingAddress.getPostalCode());
        Assert.assertEquals("United States of America", billingAddress.getCountryName());
    }

    @Test
    public void createViaTransparentRedirect() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();

        CreditCardRequest trParams = new CreditCardRequest().customerId(customer.getId());

        CreditCardRequest request = new CreditCardRequest().
            cardholderName("John Doe").
            number("5105105105105100").
            expirationDate("05/12");

        String queryString = TestHelper.simulateFormPostForTR(gateway, trParams, request, gateway.creditCard().transparentRedirectURLForCreate());
        Result<CreditCard> result = gateway.creditCard().confirmTransparentRedirect(queryString);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();
        Assert.assertEquals("John Doe", card.getCardholderName());
        Assert.assertEquals("510510", card.getBin());
        Assert.assertEquals("05", card.getExpirationMonth());
        Assert.assertEquals("2012", card.getExpirationYear());
        Assert.assertEquals("05/2012", card.getExpirationDate());
        Assert.assertEquals("5100", card.getLast4());
        Assert.assertTrue(card.getToken() != null);
    }

    @Test
    public void createViaTransparentRedirectWithMakeDefaultFlagInTRParams() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();

        CreditCardRequest request1 = new CreditCardRequest().
            customerId(customer.getId()).
            number("5105105105105100").
            expirationDate("05/12");

        gateway.creditCard().create(request1);

        CreditCardRequest trParams = new CreditCardRequest().
            customerId(customer.getId()).
            options().
                makeDefault(true).
                done();

        CreditCardRequest request2 = new CreditCardRequest().
            number("5105105105105100").
            expirationDate("05/12");

        String queryString = TestHelper.simulateFormPostForTR(gateway, trParams, request2, gateway.creditCard().transparentRedirectURLForCreate());
        CreditCard card = gateway.creditCard().confirmTransparentRedirect(queryString).getTarget();
        Assert.assertTrue(card.isDefault());
    }

    @Test
    public void createViaTransparentRedirectWithMakeDefaultFlagInRequest() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();

        CreditCardRequest request1 = new CreditCardRequest().
            customerId(customer.getId()).
            number("5105105105105100").
            expirationDate("05/12");

        gateway.creditCard().create(request1);

        CreditCardRequest trParams = new CreditCardRequest().
            customerId(customer.getId());

        CreditCardRequest request2 = new CreditCardRequest().
            number("5105105105105100").
            expirationDate("05/12").
            options().
                makeDefault(true).
                done();

        String queryString = TestHelper.simulateFormPostForTR(gateway, trParams, request2, gateway.creditCard().transparentRedirectURLForCreate());
        CreditCard card = gateway.creditCard().confirmTransparentRedirect(queryString).getTarget();
        Assert.assertTrue(card.isDefault());
    }

    @Test(expected = ForgedQueryStringException.class)
    public void createViaTransparentRedirectThrowsWhenQueryStringHasBeenTamperedWith() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest trParams = new CreditCardRequest().customerId(customer.getId());
        
        String queryString = TestHelper.simulateFormPostForTR(gateway, trParams, new CreditCardRequest(), gateway.creditCard().transparentRedirectURLForCreate());
        gateway.creditCard().confirmTransparentRedirect(queryString + "this makes it invalid");
    }
    
    @Test 
    public void createWithDefaultFlag() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();

        CreditCardRequest request1 = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            number("5105105105105100").
            expirationDate("05/12");

        CreditCardRequest request2 = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            number("5105105105105100").
            expirationDate("05/12").
            options().
                makeDefault(true).
                done();

        CreditCard card1 = gateway.creditCard().create(request1).getTarget();
        CreditCard card2 = gateway.creditCard().create(request2).getTarget();
        
        Assert.assertFalse(gateway.creditCard().find(card1.getToken()).isDefault());
        Assert.assertTrue(gateway.creditCard().find(card2.getToken()).isDefault());
    }
    
    @Test
    public void update() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();

        CreditCardRequest updateRequest = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("Jane Jones").
            cvv("321").
            number("4111111111111111").
            expirationDate("12/05");

        Result<CreditCard> updateResult = gateway.creditCard().update(card.getToken(), updateRequest);
        Assert.assertTrue(updateResult.isSuccess());
        CreditCard updatedCard = updateResult.getTarget();

        Assert.assertEquals("Jane Jones", updatedCard.getCardholderName());
        Assert.assertEquals("411111", updatedCard.getBin());
        Assert.assertEquals("12", updatedCard.getExpirationMonth());
        Assert.assertEquals("2005", updatedCard.getExpirationYear());
        Assert.assertEquals("12/2005", updatedCard.getExpirationDate());
        Assert.assertEquals("1111", updatedCard.getLast4());
        Assert.assertTrue(updatedCard.getToken() != card.getToken());
    }

    @Test
    public void updateWithDefaultFlag() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            number("5105105105105100").
            expirationDate("05/12");
        
        CreditCard card1 = gateway.creditCard().create(request).getTarget();
        CreditCard card2 = gateway.creditCard().create(request).getTarget();
        
        Assert.assertTrue(card1.isDefault());
        Assert.assertFalse(card2.isDefault());
        
        gateway.creditCard().update(card2.getToken(), new CreditCardRequest().options().makeDefault(true).done());
        Assert.assertFalse(gateway.creditCard().find(card1.getToken()).isDefault());
        Assert.assertTrue(gateway.creditCard().find(card2.getToken()).isDefault());
        
        gateway.creditCard().update(card1.getToken(), new CreditCardRequest().options().makeDefault(true).done());
        Assert.assertTrue(gateway.creditCard().find(card1.getToken()).isDefault());
        Assert.assertFalse(gateway.creditCard().find(card2.getToken()).isDefault());
    }
    
    @Test
    public void updateViaTransparentRedirect() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest createRequest = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        CreditCard card = gateway.creditCard().create(createRequest).getTarget();

        CreditCardRequest trParams = new CreditCardRequest().
            paymentMethodToken(card.getToken());

        CreditCardRequest request = new CreditCardRequest().
            cardholderName("joe cool");

        String queryString = TestHelper.simulateFormPostForTR(gateway, trParams, request, gateway.creditCard().transparentRedirectURLForUpdate());
        Result<CreditCard> result = gateway.creditCard().confirmTransparentRedirect(queryString);
        Assert.assertTrue(result.isSuccess());
        CreditCard updatedCard = result.getTarget();
        Assert.assertEquals("joe cool", updatedCard.getCardholderName());
    }

    @Test
    public void updateToken() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();

        String newToken = String.valueOf(new Random().nextInt());
        CreditCardRequest updateRequest = new CreditCardRequest().customerId(customer.getId()).token(newToken);

        Result<CreditCard> updateResult = gateway.creditCard().update(card.getToken(), updateRequest);
        Assert.assertTrue(updateResult.isSuccess());
        CreditCard updatedCard = updateResult.getTarget();

        Assert.assertEquals(newToken, updatedCard.getToken());
    }

    @Test
    public void updateOnlySomeAttributes() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();

        CreditCardRequest updateRequest = new CreditCardRequest().cardholderName("Jane Jones");

        Result<CreditCard> updateResult = gateway.creditCard().update(card.getToken(), updateRequest);
        Assert.assertTrue(updateResult.isSuccess());
        CreditCard updatedCard = updateResult.getTarget();

        Assert.assertEquals("Jane Jones", updatedCard.getCardholderName());
        Assert.assertEquals("510510", updatedCard.getBin());
        Assert.assertEquals("05", updatedCard.getExpirationMonth());
        Assert.assertEquals("2012", updatedCard.getExpirationYear());
        Assert.assertEquals("05/2012", updatedCard.getExpirationDate());
        Assert.assertEquals("5100", updatedCard.getLast4());
    }
    
    @Test
    public void updateWithBillingAddressCreatesNewAddressByDefault() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            number("5105105105105100").
            expirationDate("05/12").
            billingAddress().
                firstName("John").
                done();
        
        CreditCard creditCard = gateway.creditCard().create(request).getTarget();

        CreditCardRequest updateRequest = new CreditCardRequest().
            billingAddress().
                lastName("Jones").
                done();

        CreditCard updatedCreditCard = gateway.creditCard().update(creditCard.getToken(), updateRequest).getTarget();

        Assert.assertNull(updatedCreditCard.getBillingAddress().getFirstName());
        Assert.assertEquals("Jones", updatedCreditCard.getBillingAddress().getLastName());
        Assert.assertFalse(creditCard.getBillingAddress().getId().equals(updatedCreditCard.getBillingAddress().getId()));
    }

    @Test
    public void updateWithBillingAddressUpdatesAddressWhenUpdateExistingIsTrue() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            number("5105105105105100").
            expirationDate("05/12").
            billingAddress().
                firstName("John").
                done();
        
        CreditCard creditCard = gateway.creditCard().create(request).getTarget();

        CreditCardRequest updateRequest = new CreditCardRequest().
            billingAddress().
                lastName("Jones").
                options().
                    updateExisting(true).
                    done().
                done();

        CreditCard updatedCreditCard = gateway.creditCard().update(creditCard.getToken(), updateRequest).getTarget();

        Assert.assertEquals("John", updatedCreditCard.getBillingAddress().getFirstName());
        Assert.assertEquals("Jones", updatedCreditCard.getBillingAddress().getLastName());
        Assert.assertEquals(creditCard.getBillingAddress().getId(), updatedCreditCard.getBillingAddress().getId());
    }

    @Test
    public void updateWithBillingAddressUpdatesAddressWhenUpdateExistingIsTrueForTransparentRedirect() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            number("5105105105105100").
            expirationDate("05/12").
            billingAddress().
                firstName("John").
                done();
        
        CreditCard creditCard = gateway.creditCard().create(request).getTarget();

        CreditCardRequest trParams = new CreditCardRequest().
            paymentMethodToken(creditCard.getToken()).
            billingAddress().
                options().
                    updateExisting(true).
                    done().
                done();

        CreditCardRequest updateRequest = new CreditCardRequest().
            billingAddress().
                lastName("Jones").
                done();

        String queryString = TestHelper.simulateFormPostForTR(gateway, trParams, updateRequest, gateway.creditCard().transparentRedirectURLForUpdate());
        CreditCard updatedCard = gateway.creditCard().confirmTransparentRedirect(queryString).getTarget();
        Assert.assertEquals("John", updatedCard.getBillingAddress().getFirstName());
        Assert.assertEquals("Jones", updatedCard.getBillingAddress().getLastName());
        Assert.assertEquals(creditCard.getBillingAddress().getId(), updatedCard.getBillingAddress().getId());
    }

    @Test
    public void find() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();

        CreditCard found = gateway.creditCard().find(card.getToken());

        Assert.assertEquals("John Doe", found.getCardholderName());
        Assert.assertEquals("510510", found.getBin());
        Assert.assertEquals("05", found.getExpirationMonth());
        Assert.assertEquals("2012", found.getExpirationYear());
        Assert.assertEquals("05/2012", found.getExpirationDate());
        Assert.assertEquals("5100", found.getLast4());
    }
    
    @Test
    public void findReturnsAssociatedSubscriptions() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest cardRequest = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        CreditCard card = gateway.creditCard().create(cardRequest).getTarget();
        String id = "subscription-id-" + new Random().nextInt();
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest().
            id(id).
            planId("integration_trialless_plan").
            paymentMethodToken(card.getToken()).
            price(new BigDecimal("1.00"));
        Subscription subscription = gateway.subscription().create(subscriptionRequest).getTarget();
        
        CreditCard foundCard = gateway.creditCard().find(card.getToken());
        
        Assert.assertEquals(subscription.getId(), foundCard.getSubscriptions().get(0).getId());
        Assert.assertEquals(new BigDecimal("1.00"), foundCard.getSubscriptions().get(0).getPrice());
        Assert.assertEquals("integration_trialless_plan", foundCard.getSubscriptions().get(0).getPlanId());
    }

    @Test(expected = NotFoundException.class)
    public void findWithBadToken() {
        gateway.creditCard().find("badToken");
    }

    @Test
    public void delete() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12");
        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
        CreditCard card = result.getTarget();

        Result<CreditCard> deleteResult = gateway.creditCard().delete(card.getToken());
        Assert.assertTrue(deleteResult.isSuccess());

        try {
            gateway.creditCard().find(card.getToken());
            Assert.fail();
        } catch (NotFoundException e) {
        }
    }

    @Test
    public void verifyValidCreditCard() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("4111111111111111").
            expirationDate("05/12").
            options().
                verifyCard(true).
                done();

        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertTrue(result.isSuccess());
    }
    
    @Test
    public void verifyCreditCardAgainstSpecificMerchantAccount() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12").
            options().
                verifyCard(true).
                verificationMerchantAccountId(MerchantAccount.NON_DEFAULT_MERCHANT_ACCOUNT_ID).
                done();

        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(MerchantAccount.NON_DEFAULT_MERCHANT_ACCOUNT_ID, result.getCreditCardVerification().getMerchantAccountId());
    }

    @Test
    public void verifyInvalidCreditCard() {
        Customer customer = gateway.customer().create(new CustomerRequest()).getTarget();
        CreditCardRequest request = new CreditCardRequest().
            customerId(customer.getId()).
            cardholderName("John Doe").
            cvv("123").
            number("5105105105105100").
            expirationDate("05/12").
            options().
                verifyCard(true).
                done();

        Result<CreditCard> result = gateway.creditCard().create(request);
        Assert.assertFalse(result.isSuccess());
        CreditCardVerification verification = result.getCreditCardVerification();
        Assert.assertEquals("processor_declined", verification.getStatus());
    }
}
