/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.crypto.digest;

/**
 * Message Digest.
 *
 * @author Adam Reter <adam@evolvedbinary.com>
 */
public class MessageDigest {
    private final DigestType digestType;
    private final byte[] value;

    /**
     * @param digestType the type of the message digest
     * @param value the message digest value
     */
    public MessageDigest(final DigestType digestType, final byte[] value) {
        this.digestType = digestType;
        this.value = value;
    }

    /**
     * Get the message digest type.
     *
     * @return the message digest type.
     */
    public DigestType getDigestType() {
        return digestType;
    }

    /**
     * Get the message digest value.
     *
     * @return the message digest value.
     */
    public byte[] getValue() {
        return value;
    }
}