from calculator import add, subtract


def test_add() -> None:
    assert add(7, 5) == 12


def test_subtract() -> None:
    assert subtract(7, 5) == 2
