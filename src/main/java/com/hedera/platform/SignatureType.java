/*
 * (c) 2016-2018 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.hedera.platform;

/**
 * The type of cryptographic algorithm used to create a signature.
 *
 * @see Signature
 */
public enum SignatureType {
	/** An Ed25519 signature which uses a SHA-512 hash and a 32 byte public key */
	ED25519,
	/** An RSA signature as specified by the FIPS 186-4 standards */
	RSA,
	/** An Elliptical Curve based signature as specified by the FIPS 186-4 standards */
	ECDSA;

	private static final SignatureType[] ORDINAL_LOOKUP = values();

	/**
	 * Translates an ordinal position into an enumeration value.
	 *
	 * @param ordinal
	 * 		the ordinal value to be translated
	 * @param defaultValue
	 * 		the default enumeration value to return if the {@code ordinal} cannot be found
	 * @return the enumeration value related to the given ordinal or the default value if the ordinal is not
	 * 		found
	 */
	public static SignatureType from(final int ordinal,
			final SignatureType defaultValue) {
		if (ordinal < 0 || ordinal >= ORDINAL_LOOKUP.length) {
			return defaultValue;
		}

		return ORDINAL_LOOKUP[ordinal];
	}
}
