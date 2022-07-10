# Use Application

After you've published your application, you probably want to test your endpoint :-)

## Calculate Hash

1. Navigate to **Calculate Hash** in the main navigation.
2. Select your endpoint and the environment.
3. In case your endpoint has "include-in-hash" parameters, you will be prompted to enter values for those parameters. See: [Authentication](../../usage/authentication.md).
4. Press button **Calculate Hash** and copy the calculated hash value.

## Build the Link

1. Navigate to **Home** in the main navigation.
2. Copy **Live URL** or **Preview URL** - depending on what environment you would like to use. The URL should look like this: `https://endpoints.openendpoints.com/foo/{endpoint}?{parameter}`
3. Replace `{endpoint}` with the name of your endpoint
4. Replace `{parameter}` with: `hash=[the-calculated-hash]`
5. If your endpoint has "include-in-hash" parameters, those parameters (and the values as used for calculating the hash) shall be added as additional GET parameters.

## Test the Link

1. Copy the link into your browser.
2. Navigate to **Request-Log** in the main navigation. You will find your request, including additional information in case an error has occurred.
