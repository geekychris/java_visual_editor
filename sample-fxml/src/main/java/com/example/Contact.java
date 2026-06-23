package com.example;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Plain-old Java bean for testing the POJO binding wizard and the
 * "FXML Form from POJO" generator.
 *
 * Properties (each with matching getter + setter so the introspector finds them):
 *  - name      (String)
 *  - age       (int)
 *  - birthday  (LocalDate)
 *  - active    (boolean)
 *  - email     (String)
 *  - notes     (String)
 */
public class Contact {

    private String name = "";
    private int age = 0;
    private LocalDate birthday;
    private boolean active = true;
    private String email = "";
    private String notes = "";

    public Contact() {
    }

    public Contact(String name, int age, LocalDate birthday, boolean active, String email, String notes) {
        this.name = name;
        this.age = age;
        this.birthday = birthday;
        this.active = active;
        this.email = email;
        this.notes = notes;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return name + (active ? "" : " (inactive)");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contact)) return false;
        Contact other = (Contact) o;
        return age == other.age
            && active == other.active
            && Objects.equals(name, other.name)
            && Objects.equals(birthday, other.birthday)
            && Objects.equals(email, other.email)
            && Objects.equals(notes, other.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, birthday, active, email, notes);
    }
}
