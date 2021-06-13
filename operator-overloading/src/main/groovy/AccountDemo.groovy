class Account {

    BigDecimal balance = 0
    String type

    void deposit(BigDecimal amount) {
        balance += amount
    }

    void withdraw(BigDecimal amount) {
        balance -= amount
    }

    BigDecimal plus(Account account) {
        balance + account.balance
    }
}

Account checking = new Account(type:"Checking")
checking.deposit(100.00)
Account savings = new Account(type:"Savings")
savings.deposit(50.00)
BigDecimal total = checking + savings
println total