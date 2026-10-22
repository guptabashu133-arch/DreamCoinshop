package org.mydreamduels.dreamcoinshop;

/** The two balances. {@link #column} is the database column / Redis field. */
enum Currency {
    COINS("coins"),
    ORBS("orbs");

    final String column;

    Currency(String column) {
        this.column = column;
    }
}
